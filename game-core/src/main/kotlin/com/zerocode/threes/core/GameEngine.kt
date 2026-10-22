package com.zerocode.threes.core

import kotlin.random.Random

object GameEngine {
    private const val INITIAL_TILE_COUNT = 9

    fun newGame(random: Random = Random.Default): GameState {
        var board = Board.empty()
        var deck = TileDeck()

        repeat(INITIAL_TILE_COUNT) {
            val draw = deck.draw(random, board.highestRank())
            deck = draw.nextDeck
            val empty = board.emptyIndices()
            board = board.withTile(empty[random.nextInt(empty.size)], draw.rank)
        }

        val next = deck.draw(random, board.highestRank())
        return GameState(
            board = board,
            deck = next.nextDeck,
            nextRank = next.rank,
        )
    }

    fun move(
        state: GameState,
        direction: Direction,
        random: Random = Random.Default,
    ): GameTransition? {
        val boardMove = state.board.move(direction)
        if (!boardMove.moved) return null

        val spawnIndices = boardMove.spawnIndices()
        check(spawnIndices.isNotEmpty()) { "A valid move must expose a spawn position" }
        val spawnIndex = spawnIndices[random.nextInt(spawnIndices.size)]
        val spawnedBoard = boardMove.board.withTile(spawnIndex, state.nextRank)
        val draw = state.deck.draw(random, spawnedBoard.highestRank())
        val nextState = GameState(
            board = spawnedBoard,
            deck = draw.nextDeck,
            nextRank = draw.rank,
            moveCount = state.moveCount + 1,
        )
        return GameTransition(
            before = state,
            after = nextState,
            direction = direction,
            spawnedIndex = spawnIndex,
            spawnedRank = state.nextRank,
            scoreGain = boardMove.scoreGain,
        )
    }
}

data class GameTransition(
    val before: GameState,
    val after: GameState,
    val direction: Direction,
    val spawnedIndex: Int,
    val spawnedRank: Int,
    val scoreGain: Long,
)
