package com.zerocode.threes.ai

import com.zerocode.threes.core.Board
import com.zerocode.threes.core.Direction
import java.util.function.BooleanSupplier

internal data class NneonneoInput(val board: Long, val deck: Int, val tileset: Int) {
    companion object {
        fun from(state: AiObservation): NneonneoInput {
            val previewRank = when (state.preview) {
                TilePreview.ONE -> Board.ONE
                TilePreview.TWO -> Board.TWO
                TilePreview.THREE -> Board.THREE
                TilePreview.BONUS -> null
            }
            val counts = IntArray(3) { index -> state.deck.lowCards.count { it == index + 1 } }
            // Upstream subtracts the incoming low card at the root, while GameState
            // already stores the post-draw deck. Undo exactly that one subtraction.
            if (previewRank != null) counts[previewRank - 1]++
            val deck = counts[0] or (counts[1] shl 8) or (counts[2] shl 16)
            val tileset = state.incomingRanks().fold(0) { mask, rank -> mask or (1 shl rank) }
            return NneonneoInput(pack(state.board), deck, tileset)
        }

        fun pack(board: Board): Long = (0 until Board.CELL_COUNT).fold(0L) { bits, index ->
            bits or (board[index].toLong() shl (index * 4))
        }
    }
}

internal object NneonneoNative {
    val DIRECTIONS = listOf(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)

    init {
        val library = System.getProperty("threes.nneonneo.library")
        if (library != null) System.load(library) else System.loadLibrary("nneonneo_threes")
    }

    external fun evaluateTimed(
        board: Long, deck: Int, tileset: Int, maxDepth: Int,
        budgetNanos: Long, maxNodes: Long, cancellation: BooleanSupplier,
    ): DoubleArray
    external fun move(board: Long, direction: Int): LongArray
    /** Host verification only: compiled directly from the unmodified upstream file. */
    external fun originalScoresAtDepth(board: Long, deck: Int, tileset: Int, depth: Int): DoubleArray
}
