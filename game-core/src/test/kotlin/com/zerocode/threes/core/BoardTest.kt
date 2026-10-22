package com.zerocode.threes.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardTest {
    @Test
    fun `one and two merge into three`() {
        val board = boardWithFirstRow(1, 2, 0, 0)

        val move = board.move(Direction.LEFT)

        assertTrue(move.moved)
        assertEquals(listOf(3, 0, 0, 0), firstRow(move.board))
    }

    @Test
    fun `equal high cards merge one rank higher`() {
        val board = boardWithFirstRow(3, 3, 6, 0)

        val move = board.move(Direction.LEFT)

        assertEquals(listOf(4, 6, 0, 0), firstRow(move.board))
    }

    @Test
    fun `a line performs only the first available action`() {
        val board = boardWithFirstRow(0, 1, 2, 3)

        val move = board.move(Direction.LEFT)

        assertEquals(listOf(1, 2, 3, 0), firstRow(move.board))
    }

    @Test
    fun `blocked line does not move`() {
        val board = boardWithFirstRow(1, 3, 6, 12)

        val move = board.move(Direction.LEFT)

        assertFalse(move.moved)
        assertEquals(board, move.board)
    }

    @Test
    fun `spawn positions are on the entering edge of changed lines`() {
        val ranks = IntArray(Board.CELL_COUNT)
        ranks[1] = 1
        ranks[2] = 2
        ranks[4 + 1] = 3
        val move = Board.fromRanks(*ranks).move(Direction.LEFT)

        assertEquals(listOf(3, 7), move.spawnIndices())
    }

    @Test
    fun `move exposes source tile trajectories for animation`() {
        val move = boardWithFirstRow(1, 2, 3, 6).move(Direction.LEFT)

        assertEquals(listOf(1 to 0, 2 to 1, 3 to 2), move.motions.map { it.fromIndex to it.toIndex })
        assertTrue(move.motions.first().merges)
    }

    private fun boardWithFirstRow(a: Int, b: Int, c: Int, d: Int): Board =
        Board.fromRanks(a, b, c, d, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

    private fun firstRow(board: Board): List<Int> = (0 until Board.SIZE).map { board[it] }
}
