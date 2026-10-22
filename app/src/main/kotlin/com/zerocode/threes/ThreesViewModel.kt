package com.zerocode.threes

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerocode.threes.ai.AiDecision
import com.zerocode.threes.ai.AiPlayer
import com.zerocode.threes.ai.NneonneoTimedAi
import com.zerocode.threes.ai.SearchLimits
import com.zerocode.threes.core.Direction
import com.zerocode.threes.core.GameEngine
import com.zerocode.threes.core.GameState
import com.zerocode.threes.core.GameTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlin.random.Random

enum class AiKind(val label: String, val limits: SearchLimits) {
    MANUAL("手动模式", SearchLimits()),
    AI("AI 模式", SearchLimits());

    fun next(): AiKind = entries[(ordinal + 1) % entries.size]
}

data class GameUiState(
    val game: GameState,
    val bestScore: Long = game.score,
    val selectedAi: AiKind = AiKind.MANUAL,
    val decision: AiDecision? = null,
    val isThinking: Boolean = false,
    val isAutoRunning: Boolean = false,
    val history: List<GameState> = emptyList(),
    val lastTransition: GameTransition? = null,
    val lastMoveWasAuto: Boolean = false,
) {
    val lastMoveMerged: Boolean get() = (lastTransition?.scoreGain ?: 0L) > 0L
}

class ThreesViewModel(application: Application) : AndroidViewModel(application) {
    private val random = Random.Default
    private val player: AiPlayer = NneonneoTimedAi()
    private val scorePreferences = application.getSharedPreferences("game_stats", Context.MODE_PRIVATE)
    private val initialGame = GameEngine.newGame(random)
    private val _uiState = MutableStateFlow(GameUiState(
        game = initialGame,
        bestScore = maxOf(initialGame.score, scorePreferences.getLong("best_score", 0L)),
    ))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var searchGeneration = 0L

    init {
        if (initialGame.score > scorePreferences.getLong("best_score", 0L)) {
            scorePreferences.edit().putLong("best_score", initialGame.score).apply()
        }
    }

    fun newGame() {
        if (_uiState.value.isAutoRunning) return
        stopSearch()
        val selectedAi = _uiState.value.selectedAi
        val game = GameEngine.newGame(random)
        val bestScore = maxOf(_uiState.value.bestScore, game.score)
        if (bestScore > _uiState.value.bestScore) {
            scorePreferences.edit().putLong("best_score", bestScore).apply()
        }
        _uiState.value = GameUiState(
            game = game,
            bestScore = bestScore,
            selectedAi = selectedAi,
        )
        refreshDecision()
    }

    fun selectAi(kind: AiKind) {
        if (_uiState.value.isAutoRunning) return
        stopSearch()
        _uiState.update {
            it.copy(
                selectedAi = kind,
                decision = null,
                isThinking = false,
            )
        }
        refreshDecision()
    }

    fun move(direction: Direction) {
        if (_uiState.value.isAutoRunning || _uiState.value.game.isGameOver) return
        stopSearch()
        applyMove(direction)
        refreshDecision()
    }

    fun undo() {
        if (_uiState.value.isAutoRunning) return
        stopSearch()
        val current = _uiState.value
        val previous = current.history.lastOrNull() ?: return
        _uiState.value = current.copy(
            game = previous,
            history = current.history.dropLast(1),
            lastTransition = null,
            lastMoveWasAuto = false,
            decision = null,
            isThinking = false,
        )
        refreshDecision()
    }

    private fun refreshDecision() {
        startSearch(auto = false)
    }

    fun toggleAuto() {
        if (_uiState.value.isAutoRunning) {
            refreshDecision()
            return
        }
        if (_uiState.value.selectedAi == AiKind.MANUAL || _uiState.value.game.isGameOver) return
        startSearch(auto = true)
    }

    private fun startSearch(auto: Boolean) {
        stopSearch()
        val current = _uiState.value
        if (current.selectedAi == AiKind.MANUAL || current.game.isGameOver) return
        val generation = searchGeneration
        _uiState.update { it.copy(isThinking = true, isAutoRunning = auto, decision = null) }
        searchJob = viewModelScope.launch {
            try {
                while (isActive && !_uiState.value.game.isGameOver) {
                    val snapshot = _uiState.value
                    val decision = computeDecision(snapshot.game, snapshot.selectedAi)
                    currentCoroutineContext().ensureActive()
                    if (generation != searchGeneration || snapshot.game != _uiState.value.game ||
                        snapshot.selectedAi != _uiState.value.selectedAi
                    ) return@launch
                    _uiState.update {
                        it.copy(
                            decision = decision,
                            isThinking = false,
                        )
                    }
                    if (!auto || decision == null) break
                    // Briefly show the selected direction before playing its tile animation.
                    delay(120)
                    currentCoroutineContext().ensureActive()
                    if (!applyMove(decision.direction)) break
                    delay(330)
                    _uiState.update { it.copy(isThinking = true, decision = null) }
                }
            } finally {
                // A cancelled older search must never clear a newer search's status.
                if (generation == searchGeneration) {
                    _uiState.update { it.copy(isAutoRunning = false, isThinking = false) }
                }
            }
        }
    }

    private fun stopSearch() {
        searchGeneration++
        searchJob?.cancel()
        searchJob = null
        _uiState.update { it.copy(isAutoRunning = false, isThinking = false) }
    }

    private fun applyMove(direction: Direction): Boolean {
        val current = _uiState.value
        val transition = GameEngine.move(current.game, direction, random) ?: return false
        val bestScore = maxOf(current.bestScore, transition.after.score)
        if (bestScore > current.bestScore) {
            scorePreferences.edit().putLong("best_score", bestScore).apply()
        }
        _uiState.value = current.copy(
            game = transition.after,
            bestScore = bestScore,
            isAutoRunning = current.isAutoRunning && !transition.after.isGameOver,
            decision = null,
            history = (current.history + current.game).takeLast(MAX_HISTORY),
            lastTransition = transition,
            lastMoveWasAuto = current.isAutoRunning,
        )
        return true
    }

    private suspend fun computeDecision(game: GameState, kind: AiKind): AiDecision? {
        if (kind == AiKind.MANUAL) return null
        val context = currentCoroutineContext()
        return runInterruptible(Dispatchers.Default) {
            player.chooseMove(game, kind.limits.copy(isCancelled = { !context.isActive }))
        }
    }

    companion object {
        private const val MAX_HISTORY = 40
    }
}
