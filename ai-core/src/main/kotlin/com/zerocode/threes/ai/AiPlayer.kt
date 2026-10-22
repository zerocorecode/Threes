package com.zerocode.threes.ai

import com.zerocode.threes.core.Direction
import com.zerocode.threes.core.GameState

interface AiPlayer {
    val name: String

    fun chooseMove(
        state: GameState,
        limits: SearchLimits = SearchLimits(),
    ): AiDecision? = chooseMove(AiObservation.from(state), limits)

    fun chooseMove(
        state: AiObservation,
        limits: SearchLimits = SearchLimits(),
    ): AiDecision?
}

data class SearchLimits(
    val maxDepth: Int = 8,
    val timeBudgetMillis: Long = 200,
    val maxNodes: Long = Long.MAX_VALUE,
    val isCancelled: () -> Boolean = { false },
)

data class AiDecision(
    val direction: Direction,
    val evaluation: Double,
    val alternatives: List<MoveEvaluation>,
    val searchedNodes: Long,
    val elapsedMillis: Long,
    val completedDepth: Int,
)

data class MoveEvaluation(
    val direction: Direction,
    val evaluation: Double?,
) {
    val isLegal: Boolean get() = evaluation != null
}
