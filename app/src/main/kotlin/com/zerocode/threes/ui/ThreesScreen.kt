package com.zerocode.threes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import com.zerocode.threes.AiKind
import com.zerocode.threes.GameUiState
import com.zerocode.threes.R
import com.zerocode.threes.ai.MoveEvaluation
import com.zerocode.threes.audio.GameAudioController
import com.zerocode.threes.core.Board
import com.zerocode.threes.core.Direction
import com.zerocode.threes.core.GameTransition
import com.zerocode.threes.core.TileMotion
import com.zerocode.threes.ui.theme.BoardMint
import com.zerocode.threes.ui.theme.EmptyMint
import com.zerocode.threes.ui.theme.Ink
import com.zerocode.threes.ui.theme.OneBlue
import com.zerocode.threes.ui.theme.OneBlueEdge
import com.zerocode.threes.ui.theme.SoftWhite
import com.zerocode.threes.ui.theme.ThreeGold
import com.zerocode.threes.ui.theme.ThreeGoldEdge
import com.zerocode.threes.ui.theme.TwoRed
import com.zerocode.threes.ui.theme.TwoRedEdge
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val TileFont = FontFamily(
    Font(R.font.numbers_premium, weight = FontWeight.Normal),
)

@Composable
fun ThreesApp(
    state: GameUiState,
    onNewGame: () -> Unit,
    onSelectAi: (AiKind) -> Unit,
    onMove: (Direction) -> Unit,
    onToggleAuto: () -> Unit,
    onUndo: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioPreferences = remember(context) {
        context.getSharedPreferences("game_audio", android.content.Context.MODE_PRIVATE)
    }
    val audioController = remember(context) {
        GameAudioController(context.applicationContext)
    }
    var audioEnabled by rememberSaveable {
        mutableStateOf(audioPreferences.getBoolean("enabled", true))
    }
    var autoAudioEnabled by rememberSaveable { mutableStateOf(false) }
    val effectiveAudioEnabled = if (state.isAutoRunning) autoAudioEnabled else audioEnabled
    var previousMoveCount by remember { mutableStateOf(state.game.moveCount) }
    var showNewGameConfirmation by rememberSaveable { mutableStateOf(false) }
    var gameOverDismissed by rememberSaveable(state.game.isGameOver) { mutableStateOf(false) }
    var gameOverCardReady by remember(state.game.isGameOver) { mutableStateOf(false) }

    LaunchedEffect(state.game.isGameOver) {
        if (state.game.isGameOver) {
            // Let the final move and the tile score labels appear before the result card.
            delay(1_000)
            gameOverCardReady = true
        }
    }

    DisposableEffect(lifecycleOwner, audioController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> audioController.setForeground(true)
                Lifecycle.Event.ON_STOP -> audioController.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        audioController.setForeground(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(audioController) {
        onDispose(audioController::release)
    }
    LaunchedEffect(effectiveAudioEnabled) {
        audioController.setEnabled(effectiveAudioEnabled)
    }
    LaunchedEffect(state.game.moveCount) {
        // A terminal automatic move clears isAutoRunning immediately. Keep that
        // move muted too when restoring the manual sound preference.
        if (state.game.moveCount > previousMoveCount && (!state.lastMoveWasAuto || autoAudioEnabled)) {
            audioController.playMove(merged = state.lastMoveMerged)
        }
        previousMoveCount = state.game.moveCount
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.dp))
            ScoreRow(state = state)
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val boardWidth = minOf(maxWidth, maxHeight * 0.72f, 500.dp)
                Box(modifier = Modifier.width(boardWidth)) {
                    GameBoard(
                        board = state.game.board,
                        lastTransition = state.lastTransition,
                        isGameOver = state.game.isGameOver,
                        enabled = !state.isAutoRunning && !state.game.isGameOver,
                        onInvalidMove = audioController::playInvalidMove,
                        onMove = onMove,
                    )
                    if (state.game.isGameOver && gameOverCardReady && !gameOverDismissed) {
                        GameOverCard(
                            state = state,
                            onReview = {
                                audioController.playClick()
                                gameOverDismissed = true
                            },
                            onReplay = {
                                audioController.playClick()
                                onNewGame()
                            },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
            DecisionSummary(state, audioController::playClick)
            ActionRow(
                state = state,
                onSelectAi = {
                    audioController.playClick()
                    onSelectAi(it)
                },
                onToggleAuto = {
                    audioController.playDeepClick(allowWhenDisabled = audioEnabled)
                    if (!state.isAutoRunning) autoAudioEnabled = false
                    onToggleAuto()
                },
                onUndo = {
                    audioController.playClick()
                    onUndo()
                },
                audioEnabled = effectiveAudioEnabled,
                onToggleAudio = {
                    val wasEnabled = effectiveAudioEnabled
                    if (wasEnabled) audioController.playClick()
                    if (state.isAutoRunning) {
                        autoAudioEnabled = !autoAudioEnabled
                        audioController.setEnabled(autoAudioEnabled)
                    } else {
                        audioEnabled = !audioEnabled
                        audioPreferences.edit().putBoolean("enabled", audioEnabled).apply()
                        audioController.setEnabled(audioEnabled)
                    }
                    if (!wasEnabled) audioController.playClick()
                },
                onNewGame = {
                    audioController.playClick()
                    if (state.game.isGameOver) onNewGame() else showNewGameConfirmation = true
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showNewGameConfirmation) {
        NewGameConfirmationDialog(
            onDismiss = {
                audioController.playClick()
                showNewGameConfirmation = false
            },
            onConfirm = {
                audioController.playClick()
                showNewGameConfirmation = false
                onNewGame()
            },
        )
    }
}

@Composable
private fun GameOverCard(
    state: GameUiState,
    onReview: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Ink.copy(alpha = 0.28f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "本局结束",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = formatNumber(state.game.score),
                    fontFamily = TileFont,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "最大方块 ${formatNumber(Board.displayValue(state.game.board.highestRank()).toLong())} · ${state.game.moveCount} 步",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onReview,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("查看棋盘", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(
                        onClick = onReplay,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                    ) {
                        Text("再来一局", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewGameConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "再来一局？",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "当前局面和撤销记录将被清除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Ink,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("再来一局")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(
    state: GameUiState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreBlock("分数", state.game.score)
            ScoreBlock("最佳", state.bestScore, color = ThreeGold)
        }
        Surface(shape = RoundedCornerShape(6.dp), color = BoardMint) {
            Column(
                modifier = Modifier.width(84.dp).height(65.dp).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.width(34.dp).height(46.dp)) {
                    ThreesTile(
                        rank = state.game.nextRank,
                        compact = true,
                        showAsBonus = state.game.nextRank > Board.THREE,
                        showNumber = state.game.nextRank >= Board.THREE,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreBlock(label: String, score: Long, color: Color = BoardMint) {
    val formatted = formatCompactScore(score)
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Column(
            modifier = Modifier.width(84.dp).height(65.dp).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, fontSize = 11.sp, color = Ink.copy(alpha = 0.75f))
            Text(
                text = formatted,
                fontFamily = TileFont,
                fontSize = if (formatted.length >= 8) 15.sp else 19.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GameBoard(
    board: Board,
    lastTransition: GameTransition?,
    isGameOver: Boolean,
    enabled: Boolean,
    onInvalidMove: () -> Unit,
    onMove: (Direction) -> Unit,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var isSettling by remember { mutableStateOf(false) }
    var animationBoard by remember { mutableStateOf<Board?>(null) }
    var settlingMotions by remember { mutableStateOf(emptyList<TileMotion>()) }
    var reboundDirection by remember { mutableStateOf<Direction?>(null) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val slideProgress = remember { Animatable(0f) }
    val reboundDistance = remember { Animatable(0f) }
    val animationScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val latestBoard = rememberUpdatedState(board)
    var mergeScores by remember { mutableStateOf(emptyList<TileScoreLabel>()) }
    val scoreProgress = remember { Animatable(0f) }
    val mergeProgress = remember { Animatable(0f) }
    var activeMergeTransition by remember { mutableStateOf<GameTransition?>(null) }
    var completedMergeTransition by remember { mutableStateOf<GameTransition?>(null) }
    val mergedIndices = remember(lastTransition) {
        lastTransition?.let { transition ->
            transition.before.board.move(transition.direction).motions
                .filter { it.merges }.map { it.toIndex }.toSet()
        }.orEmpty()
    }

    LaunchedEffect(board, lastTransition) {
        if (lastTransition?.after?.board != board) {
            activeMergeTransition = null
            completedMergeTransition = null
            return@LaunchedEffect
        }
        if (mergedIndices.isEmpty()) {
            completedMergeTransition = lastTransition
            return@LaunchedEffect
        }
        snapshotFlow { isSettling }.first { !it }
        mergeProgress.snapTo(0f)
        activeMergeTransition = lastTransition
        try {
            mergeProgress.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        } finally {
            completedMergeTransition = lastTransition
            activeMergeTransition = null
        }
    }

    LaunchedEffect(board, lastTransition, isGameOver) {
        mergeScores = emptyList()
        if (isGameOver || lastTransition?.after?.board != board) return@LaunchedEffect
        val move = lastTransition.before.board.move(lastTransition.direction)
        val scores = move.motions.filter { it.merges }.mapNotNull { motion ->
            val rank = move.board[motion.toIndex]
            if (Board.displayValue(rank) >= 48) {
                TileScoreLabel(motion.toIndex, Board.scoreForRank(rank))
            } else {
                null
            }
        }
        if (scores.isEmpty()) return@LaunchedEffect
        // Manual moves publish their result at the end of the slide. Wait for the
        // animated source tiles to be replaced before drawing on the merged tiles.
        snapshotFlow { isSettling }.first { !it }
        scoreProgress.snapTo(0f)
        mergeScores = scores
        scoreProgress.animateTo(1f, tween(durationMillis = 760, easing = LinearEasing))
        mergeScores = emptyList()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(15.dp))
            .background(BoardMint)
            .padding(8.dp),
    ) {
        val gap = 7.dp
        val cellWidth = (maxWidth - gap * (Board.SIZE - 1)) / Board.SIZE
        val cellHeight = (maxHeight - gap * (Board.SIZE - 1)) / Board.SIZE
        val strideX = cellWidth + gap
        val strideY = cellHeight + gap
        val strideXPx = with(density) { strideX.toPx() }
        val strideYPx = with(density) { strideY.toPx() }
        val maximumReboundPx = with(density) { 18.dp.toPx() }
        val renderedBoard = animationBoard ?: board
        val mergeTransition = lastTransition?.takeIf {
            it.after.board == board && it != completedMergeTransition && mergedIndices.isNotEmpty()
        }
        val previewDirection = directionForPreview(drag)
        val previewMove = previewDirection?.let(renderedBoard::move)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, board, strideXPx, strideYPx, isSettling, mergeTransition) {
                if (!enabled || isSettling || mergeTransition != null) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        animationJob?.cancel()
                        mergeScores = emptyList()
                        drag = Offset.Zero
                        reboundDirection = null
                        animationScope.launch {
                            slideProgress.snapTo(0f)
                            reboundDistance.snapTo(0f)
                        }
                    },
                    onDragEnd = {
                        val releasedDrag = drag
                        drag = Offset.Zero
                        val direction = directionForDrag(releasedDrag) ?: return@detectDragGestures
                        val move = board.move(direction)
                        if (move.moved) {
                            val sourceBoard = board
                            animationBoard = sourceBoard
                            settlingMotions = move.motions
                            isSettling = true
                            val stride = directionStride(direction, strideXPx, strideYPx)
                            val start = (directionDistance(direction, releasedDrag) / stride)
                                .coerceIn(0f, 0.72f)
                            animationJob = animationScope.launch {
                                slideProgress.snapTo(start)
                                slideProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                                onMove(direction)
                                var framesWaited = 0
                                while (latestBoard.value == sourceBoard && framesWaited < 4) {
                                    withFrameNanos { }
                                    framesWaited++
                                }
                                animationBoard = null
                                isSettling = false
                                slideProgress.snapTo(0f)
                                settlingMotions = emptyList()
                            }
                        } else {
                            onInvalidMove()
                            reboundDirection = direction
                            val start = min(
                                directionDistance(direction, releasedDrag),
                                maximumReboundPx,
                            ).coerceAtLeast(maximumReboundPx * 0.7f)
                            animationJob = animationScope.launch {
                                reboundDistance.snapTo(start)
                                reboundDistance.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.38f,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                                reboundDirection = null
                            }
                        }
                    },
                    onDragCancel = {
                        drag = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                )
            },
        )

        repeat(Board.CELL_COUNT) { index ->
            val row = index / Board.SIZE
            val column = index % Board.SIZE
            Box(
                modifier = Modifier
                    .offset(x = strideX * column, y = strideY * row)
                    .size(cellWidth, cellHeight)
                    .clip(RoundedCornerShape(7.dp))
                    .background(EmptyMint),
            )
        }

        repeat(Board.CELL_COUNT) { index ->
            val rank = renderedBoard[index]
            if (rank != Board.EMPTY) {
                val row = index / Board.SIZE
                val column = index % Board.SIZE
                val motion = when {
                    isSettling -> settlingMotions.firstOrNull { it.fromIndex == index }
                    previewMove?.moved == true -> previewMove.motions.firstOrNull { it.fromIndex == index }
                    else -> null
                }
                val motionProgress = when {
                    isSettling -> slideProgress.value
                    motion != null && previewDirection != null -> {
                        val stride = directionStride(previewDirection, strideXPx, strideYPx)
                        (directionDistance(previewDirection, drag) / stride).coerceIn(0f, 0.72f)
                    }
                    else -> 0f
                }
                val movement = motion?.let {
                    tileMotionOffset(it, strideXPx, strideYPx) * motionProgress
                } ?: Offset.Zero
                val invalidDrag = if (
                    !isSettling && previewDirection != null && previewMove?.moved == false
                ) {
                    directionOffset(
                        previewDirection,
                        min(directionDistance(previewDirection, drag), maximumReboundPx),
                    )
                } else {
                    Offset.Zero
                }
                val rebound = reboundDirection?.let {
                    directionOffset(it, reboundDistance.value)
                } ?: Offset.Zero
                // Start the result at its small size even before the effect runs,
                // so the first frame cannot flash at full size and then shrink.
                val scale = if (!isSettling && animationBoard == null &&
                    mergeTransition != null && index in mergedIndices
                ) {
                    val progress = if (activeMergeTransition == mergeTransition) mergeProgress.value else 0f
                    0.82f + 0.18f * progress
                } else 1f

                Box(
                    modifier = Modifier
                        .offset(x = strideX * column, y = strideY * row)
                        .size(cellWidth, cellHeight)
                        .zIndex(if (motion != null) 1f else 0f)
                        .graphicsLayer {
                            translationX = movement.x + invalidDrag.x + rebound.x
                            translationY = movement.y + invalidDrag.y + rebound.y
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    ThreesTile(rank)
                }
            }
        }

        val finalScores = if (isGameOver && !isSettling) {
            (0 until Board.CELL_COUNT).mapNotNull { index ->
                Board.scoreForRank(board[index]).takeIf { it > 0L }?.let {
                    TileScoreLabel(index, it)
                }
            }
        } else {
            emptyList()
        }
        TileScoreLabels(
            scores = if (isGameOver) finalScores else mergeScores,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            strideX = strideX,
            strideY = strideY,
            progress = if (isGameOver) 0f else scoreProgress.value,
            persistent = isGameOver,
            modifier = Modifier.fillMaxSize().zIndex(2f),
        )
    }
}

private data class TileScoreLabel(val index: Int, val score: Long)

@Composable
private fun TileScoreLabels(
    scores: List<TileScoreLabel>,
    cellWidth: Dp,
    cellHeight: Dp,
    strideX: Dp,
    strideY: Dp,
    progress: Float,
    persistent: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val typeface = remember(context) {
        androidx.core.content.res.ResourcesCompat.getFont(context, R.font.numbers_premium)
    }
    val paint = remember(typeface) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = android.graphics.Paint.Align.CENTER
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    Canvas(modifier) {
        val opacity = if (persistent) 1f else when {
            progress < 0.10f -> progress / 0.10f
            progress < 0.55f -> 1f
            else -> (1f - progress) / 0.45f
        }
        val rise = if (persistent) 0f else 10.dp.toPx() * progress
        scores.forEach { label ->
            val text = "+${formatNumber(label.score)}"
            val baseSize = min(28.sp.toPx(), cellHeight.toPx() * 0.23f)
            paint.textSize = baseSize
            val availableWidth = (cellWidth.toPx() - 8.dp.toPx()).coerceAtLeast(1f)
            val measuredWidth = paint.measureText(text)
            if (measuredWidth > availableWidth) {
                paint.textSize *= availableWidth / measuredWidth
            }
            val centerX = strideX.toPx() * (label.index % Board.SIZE) + cellWidth.toPx() / 2f
            val baseline = strideY.toPx() * (label.index / Board.SIZE) +
                2.dp.toPx() - paint.fontMetrics.ascent - rise
            paint.strokeWidth = (paint.textSize * 0.14f).coerceIn(1.5.dp.toPx(), 3.dp.toPx())
            drawIntoCanvas { canvas ->
                paint.style = android.graphics.Paint.Style.STROKE
                paint.color = android.graphics.Color.BLACK
                paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                canvas.nativeCanvas.drawText(text, centerX, baseline, paint)
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = android.graphics.Color.rgb(255, 202, 53)
                paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                canvas.nativeCanvas.drawText(text, centerX, baseline, paint)
            }
        }
    }
}

@Composable
private fun ThreesTile(
    rank: Int,
    compact: Boolean = false,
    showAsBonus: Boolean = false,
    showNumber: Boolean = true,
) {
    val colors = tileColors(if (showAsBonus) Board.THREE else rank)
    val shape = RoundedCornerShape(if (compact) 5.dp else 7.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(colors.edge),
    ) {
        if (rank == Board.EMPTY) {
            Box(Modifier.fillMaxSize().background(EmptyMint))
            return@Box
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (compact) Modifier.fillMaxSize().padding(bottom = 4.dp)
                    else Modifier.fillMaxSize().padding(bottom = 6.dp),
                )
                .clip(shape)
                .background(colors.face),
            contentAlignment = Alignment.Center,
        ) {
            val display = if (showAsBonus) "+" else Board.displayValue(rank).toString()
            if (showNumber) Text(
                text = display,
                color = colors.text,
                fontFamily = TileFont,
                fontWeight = FontWeight.Bold,
                fontSize = tileFontSize(display.length, compact),
                textAlign = TextAlign.Center,
                style = TextStyle(letterSpacing = 0.sp),
                modifier = Modifier.offset(y = if (compact) (-1).dp else (-2).dp),
            )
        }
        if (rank >= Board.THREE && !compact) {
            TileFace(
                color = colors.detail,
                modifier = Modifier.align(Alignment.BottomCenter).height(6.dp),
            )
        }
    }
}

@Composable
private fun TileFace(color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(2.dp).clip(CircleShape).background(color))
        Box(Modifier.width(7.dp).height(1.dp).clip(CircleShape).background(color))
        Box(Modifier.size(2.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun DecisionSummary(state: GameUiState, onClickSound: () -> Unit) {
    val modeColor = aiModeColor(state.selectedAi)
    val decision = state.decision
    val secondaryColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
    val depthText = decision?.completedDepth?.toString() ?: if (state.isThinking) "…" else "—"
    val nodesText = decision?.searchedNodes?.let { nodes ->
        when {
            nodes >= 1_000_000 -> "%.1fM".format(java.util.Locale.ROOT, nodes / 1_000_000.0)
            nodes >= 10_000 -> "%.1fk".format(java.util.Locale.ROOT, nodes / 1_000.0)
            else -> formatNumber(nodes)
        }
    } ?: if (state.isThinking) "…" else "—"
    val elapsedText = decision?.elapsedMillis?.let { millis ->
        if (millis >= 1_000) "%.1fs".format(java.util.Locale.ROOT, millis / 1_000.0) else "${millis}ms"
    } ?: if (state.isThinking) "…" else "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = state.selectedAi.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = modeColor,
                maxLines = 1,
            )
            Text(
                text = "深度 $depthText · 节点 $nodesText · 耗时 $elapsedText",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                maxLines = 1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val evaluations = decision?.alternatives?.associateBy { it.direction }.orEmpty()
            Direction.entries.forEach { direction ->
                DirectionScoreButton(
                    evaluation = evaluations[direction] ?: MoveEvaluation(direction, null),
                    selected = decision?.direction == direction,
                    resultAvailable = decision != null,
                    isThinking = state.isThinking,
                    manualMode = state.selectedAi == AiKind.MANUAL,
                    modeColor = modeColor,
                    enabled = !state.isAutoRunning,
                    onClickSound = onClickSound,
                )
            }
        }
    }
}

@Composable
private fun DirectionScoreButton(
    evaluation: MoveEvaluation,
    selected: Boolean,
    resultAvailable: Boolean,
    isThinking: Boolean,
    manualMode: Boolean,
    modeColor: Color,
    enabled: Boolean,
    onClickSound: () -> Unit,
) {
    var showScore by remember(evaluation.direction) { mutableStateOf(false) }
    val popupOffset = with(LocalDensity.current) { IntOffset(0, -56.dp.roundToPx()) }
    val value = evaluation.evaluation
    val faceColor = when {
        selected -> modeColor
        value != null -> modeColor.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    }
    val iconColor = when {
        selected -> Color.White
        value != null -> modeColor
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
    }

    Box(contentAlignment = Alignment.Center) {
        Surface(
            onClick = {
                onClickSound()
                showScore = true
            },
            enabled = enabled,
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = faceColor,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) modeColor else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                DirectionIcon(evaluation.direction, tint = iconColor, size = 22.dp)
            }
        }
        if (showScore) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = popupOffset,
                onDismissRequest = { showScore = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Ink,
                    shadowElevation = 5.dp,
                ) {
                    Text(
                        text = when {
                            value != null -> "${directionLabel(evaluation.direction)}评分 ${"%.1f".format(value)}"
                            isThinking -> "${directionLabel(evaluation.direction)}评分计算中"
                            resultAvailable -> "${directionLabel(evaluation.direction)}不可移动"
                            manualMode -> "手动模式无${directionLabel(evaluation.direction)}评分"
                            else -> "暂无${directionLabel(evaluation.direction)}评分"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    state: GameUiState,
    onSelectAi: (AiKind) -> Unit,
    onToggleAuto: () -> Unit,
    onUndo: () -> Unit,
    audioEnabled: Boolean,
    onToggleAudio: () -> Unit,
    onNewGame: () -> Unit,
) {
    val modeColor = aiModeColor(state.selectedAi)
    val modeIconColor = if (state.selectedAi == AiKind.MANUAL) Ink else modeColor
    val nextMode = state.selectedAi.next()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = { onSelectAi(nextMode) },
            enabled = !state.isAutoRunning && !state.game.isGameOver,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    contentDescription = "切换AI模式，当前${state.selectedAi.label}",
                    tint = modeIconColor.copy(alpha = if (state.isAutoRunning || state.game.isGameOver) 0.3f else 1f),
                )
            }
        }
        Button(
            onClick = onToggleAuto,
            enabled = state.selectedAi != AiKind.MANUAL && !state.game.isGameOver,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        ) {
            Icon(if (state.isAutoRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(5.dp))
            Text(if (state.isAutoRunning) "暂停" else "AI 自动", maxLines = 1)
        }
        BottomActionButton(
            onClick = onUndo,
            enabled = !state.isAutoRunning && state.history.isNotEmpty(),
            icon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤销") },
        )
        BottomActionButton(
            onClick = onToggleAudio,
            icon = {
                Icon(
                    imageVector = if (audioEnabled) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    },
                    contentDescription = if (audioEnabled) "关闭声音" else "开启声音",
                )
            },
        )
        BottomActionButton(
            onClick = onNewGame,
            enabled = !state.isAutoRunning,
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = "再来一局") },
        )
    }
}

@Composable
private fun BottomActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

private fun aiModeColor(kind: AiKind): Color = when (kind) {
    AiKind.MANUAL -> Color(0xFF858A92)
    AiKind.AI -> Color(0xFF4772B5)
}

@Composable
private fun DirectionIcon(direction: Direction, tint: Color, size: Dp = 22.dp) {
    val icon = when (direction) {
        Direction.LEFT -> Icons.AutoMirrored.Rounded.ArrowBack
        Direction.UP -> Icons.Rounded.ArrowUpward
        Direction.RIGHT -> Icons.AutoMirrored.Rounded.ArrowForward
        Direction.DOWN -> Icons.Rounded.ArrowDownward
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

private fun directionForDrag(offset: Offset): Direction? {
    val threshold = 42f
    if (abs(offset.x) < threshold && abs(offset.y) < threshold) return null
    return if (abs(offset.x) > abs(offset.y)) {
        if (offset.x < 0) Direction.LEFT else Direction.RIGHT
    } else {
        if (offset.y < 0) Direction.UP else Direction.DOWN
    }
}

private fun directionForPreview(offset: Offset): Direction? {
    val threshold = 6f
    if (abs(offset.x) < threshold && abs(offset.y) < threshold) return null
    return if (abs(offset.x) > abs(offset.y)) {
        if (offset.x < 0) Direction.LEFT else Direction.RIGHT
    } else {
        if (offset.y < 0) Direction.UP else Direction.DOWN
    }
}

private fun directionDistance(direction: Direction, offset: Offset): Float = when (direction) {
    Direction.LEFT, Direction.RIGHT -> abs(offset.x)
    Direction.UP, Direction.DOWN -> abs(offset.y)
}

private fun directionStride(direction: Direction, strideX: Float, strideY: Float): Float =
    when (direction) {
        Direction.LEFT, Direction.RIGHT -> strideX
        Direction.UP, Direction.DOWN -> strideY
    }

private fun directionOffset(direction: Direction, distance: Float): Offset = when (direction) {
    Direction.LEFT -> Offset(-distance, 0f)
    Direction.UP -> Offset(0f, -distance)
    Direction.RIGHT -> Offset(distance, 0f)
    Direction.DOWN -> Offset(0f, distance)
}

private fun tileMotionOffset(motion: TileMotion, strideX: Float, strideY: Float): Offset {
    val fromRow = motion.fromIndex / Board.SIZE
    val fromColumn = motion.fromIndex % Board.SIZE
    val toRow = motion.toIndex / Board.SIZE
    val toColumn = motion.toIndex % Board.SIZE
    return Offset(
        x = (toColumn - fromColumn) * strideX,
        y = (toRow - fromRow) * strideY,
    )
}

private fun directionLabel(direction: Direction): String = when (direction) {
    Direction.LEFT -> "向左"
    Direction.UP -> "向上"
    Direction.RIGHT -> "向右"
    Direction.DOWN -> "向下"
}

private data class TileColors(
    val face: Color,
    val edge: Color,
    val text: Color,
    val detail: Color,
)

private fun tileColors(rank: Int): TileColors = when (rank) {
    Board.EMPTY -> TileColors(EmptyMint, EmptyMint, Color.Transparent, Color.Transparent)
    Board.ONE -> TileColors(OneBlue, OneBlueEdge, Color.White, Color.White)
    Board.TWO -> TileColors(TwoRed, TwoRedEdge, Color.White, Color.White)
    Board.THREE -> TileColors(SoftWhite, ThreeGold, Ink, ThreeGoldEdge)
    else -> {
        val text = when (rank % 4) {
            0 -> OneBlueEdge
            1 -> TwoRedEdge
            2 -> ThreeGoldEdge
            else -> Ink
        }
        TileColors(SoftWhite, ThreeGold, text, text.copy(alpha = 0.82f))
    }
}

private fun tileFontSize(digits: Int, compact: Boolean) = when {
    compact -> 23.sp
    digits <= 1 -> 44.sp
    digits == 2 -> 39.sp
    digits == 3 -> 33.sp
    digits == 4 -> 27.sp
    else -> 22.sp
}

private fun formatNumber(value: Long): String = "%,d".format(value)

private fun formatCompactScore(value: Long): String = when {
    value >= 1_000_000_000_000 -> "%.1fT".format(java.util.Locale.ROOT, value / 1_000_000_000_000.0)
    value >= 1_000_000_000 -> "%.1fB".format(java.util.Locale.ROOT, value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(java.util.Locale.ROOT, value / 1_000_000.0)
    value >= 100_000 -> "%.0fK".format(java.util.Locale.ROOT, value / 1_000.0)
    else -> formatNumber(value)
}
