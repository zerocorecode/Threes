package com.zerocode.threes.core

data class GameState(
    val board: Board,
    val deck: TileDeck,
    val nextRank: Int,
    val moveCount: Int = 0,
    val score: Long = board.score(),
) {
    val legalDirections: List<Direction> get() = board.legalDirections()
    val isGameOver: Boolean get() = legalDirections.isEmpty()
}
