package com.zerocode.threes.ai

import com.zerocode.threes.core.Board
import com.zerocode.threes.core.Direction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeBoardTest {
    @Test
    fun `all 65536 rows agree with the game engine in both directions`() {
        for (row in 0..0xffff) {
            val ranks = IntArray(Board.CELL_COUNT)
            repeat(4) { ranks[it] = (row ushr (it * 4)) and 0xf }
            val board = Board.fromRanks(ranks.toList())
            for (direction in listOf(Direction.LEFT, Direction.RIGHT)) assertMove(board, direction)
        }
    }

    @Test
    fun `all directions and changed line masks agree on 2000 random full boards`() {
        val random = Random(42)
        repeat(2_000) {
            val board = Board.fromRanks(List(Board.CELL_COUNT) { random.nextInt(16) })
            Direction.entries.forEach { assertMove(board, it) }
        }
    }

    private fun assertMove(board: Board, direction: Direction) {
        val expected = board.move(direction)
        val native = NneonneoNative.move(NneonneoInput.pack(board), NneonneoNative.DIRECTIONS.indexOf(direction))
        assertEquals(NneonneoInput.pack(expected.board), native[0], "$direction on $board")
        val mask = expected.changedLines.fold(0) { bits, line -> bits or (1 shl line) }
        assertEquals((expected.changedLines.size shl 8) or mask, native[1].toInt())
    }
}
