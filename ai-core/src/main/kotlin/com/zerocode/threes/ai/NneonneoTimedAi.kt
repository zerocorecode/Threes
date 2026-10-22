package com.zerocode.threes.ai

import com.zerocode.threes.core.Direction
import java.util.function.BooleanSupplier

/** Separate adaptation: original heuristic/search model with budgeted complete rounds. */
class NneonneoTimedAi : AiPlayer {
    override val name: String = "AI 模式"

    override fun chooseMove(state: AiObservation, limits: SearchLimits): AiDecision? {
        if (state.isGameOver) return null
        val started = System.nanoTime()
        val input = NneonneoInput.from(state)
        val nanos = limits.timeBudgetMillis.coerceIn(1, Long.MAX_VALUE / 1_000_000) * 1_000_000
        val values = NneonneoNative.evaluateTimed(
            input.board, input.deck, input.tileset, limits.maxDepth.coerceAtLeast(1),
            (nanos - (System.nanoTime() - started)).coerceAtLeast(1), limits.maxNodes.coerceAtLeast(1),
            BooleanSupplier { limits.isCancelled() || Thread.currentThread().isInterrupted },
        )
        val directions = NneonneoNative.DIRECTIONS
        val chosen = values[4].toInt()
        check(chosen in directions.indices) { "Timed search returned no move for a legal board" }
        val legal = state.legalDirections
        return AiDecision(
            direction = directions[chosen],
            evaluation = values[chosen],
            alternatives = Direction.entries.map { direction ->
                MoveEvaluation(direction, values[directions.indexOf(direction)].takeIf { direction in legal })
            },
            searchedNodes = values[5].toLong(),
            elapsedMillis = (System.nanoTime() - started) / 1_000_000,
            completedDepth = values[6].toInt(),
        )
    }
}
