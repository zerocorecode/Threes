package com.zerocode.threes.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameEngineTest {
    @Test
    fun `new game starts with nine tiles and a preview`() {
        val game = GameEngine.newGame(Random(7))

        assertEquals(9, Board.CELL_COUNT - game.board.emptyIndices().size)
        assertTrue(game.nextRank in 1..3)
        assertEquals(0, game.moveCount)
    }

    @Test
    fun `valid move spawns preview tile on entering edge`() {
        val board = Board.fromRanks(
            0, 1, 0, 0,
            0, 2, 0, 0,
            0, 3, 0, 0,
            0, 6, 0, 0,
        )
        val state = GameState(board, TileDeck(), nextRank = 2)

        val transition = assertNotNull(GameEngine.move(state, Direction.LEFT, Random(3)))

        assertTrue(transition.spawnedIndex in listOf(3, 7, 11, 15))
        assertEquals(2, transition.after.board[transition.spawnedIndex])
        assertEquals(1, transition.after.moveCount)
    }

    @Test
    fun `bonus spawning raises score without reporting a merge`() {
        val state = GameState(Board.empty().withTile(1, 8), TileDeck(), nextRank = 4)

        val transition = assertNotNull(GameEngine.move(state, Direction.LEFT, Random(11)))

        assertTrue(transition.after.score > state.score)
        assertEquals(0L, transition.scoreGain)
    }

    @Test
    fun `merges are reported independently of the incoming bonus tile`() {
        val board = Board.fromRanks(
            3, 3, 0, 0,
            0, 8, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )
        val state = GameState(board, TileDeck(), nextRank = 4)

        val transition = assertNotNull(GameEngine.move(state, Direction.LEFT, Random(11)))

        assertTrue(transition.scoreGain > 0)
        assertEquals(4, transition.spawnedRank)
    }

    @Test
    fun `deck outcome probabilities sum to one`() {
        val outcomes = TileDeck().outcomes(highestRank = 8)

        assertTrue(kotlin.math.abs(outcomes.sumOf { it.probability } - 1.0) < 0.000001)
        assertTrue(outcomes.any { it.rank > Board.THREE })
    }
}
