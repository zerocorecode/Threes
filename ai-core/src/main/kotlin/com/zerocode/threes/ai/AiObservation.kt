package com.zerocode.threes.ai

import com.zerocode.threes.core.Board
import com.zerocode.threes.core.Direction
import com.zerocode.threes.core.GameState
import com.zerocode.threes.core.TileDeck

/** The preview shown to the player, without the hidden value behind a bonus card. */
enum class TilePreview {
    ONE, TWO, THREE, BONUS;

    companion object {
        fun fromRank(rank: Int): TilePreview = when (rank) {
            Board.ONE -> ONE
            Board.TWO -> TWO
            Board.THREE -> THREE
            else -> {
                require(rank > Board.THREE)
                BONUS
            }
        }
    }
}

/** Search nodes retain deck counts, but never retain a bonus preview's actual rank. */
data class AiObservation(
    val board: Board,
    val deck: TileDeck,
    val preview: TilePreview,
    val moveCount: Int,
) {
    val legalDirections: List<Direction> get() = board.legalDirections()
    val isGameOver: Boolean get() = legalDirections.isEmpty()

    internal fun incomingRanks(): List<Int> = when (preview) {
        TilePreview.ONE -> listOf(Board.ONE)
        TilePreview.TWO -> listOf(Board.TWO)
        TilePreview.THREE -> listOf(Board.THREE)
        // The preview was drawn before this move, so a merge cannot enlarge its range.
        TilePreview.BONUS -> TileDeck.bonusRanks(board.highestRank()).also {
            check(it.isNotEmpty()) { "Bonus preview requires an eligible board" }
        }
    }

    companion object {
        fun from(state: GameState): AiObservation = AiObservation(
            board = state.board,
            deck = state.deck,
            preview = TilePreview.fromRank(state.nextRank),
            moveCount = state.moveCount,
        )
    }
}
