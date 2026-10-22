package com.zerocode.threes.ai

import com.zerocode.threes.core.Board
import com.zerocode.threes.core.Direction
import com.zerocode.threes.core.GameEngine
import com.zerocode.threes.core.GameState
import com.zerocode.threes.core.TileDeck
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AiObservationTest {
    @Test
    fun `known preview is added back to the pre-draw deck exactly once`() {
        val state = GameEngine.newGame(Random(17))
        for (rank in 1..3) {
            val input = NneonneoInput.from(AiObservation.from(state.copy(deck = TileDeck(listOf(1, 2, 3)), nextRank = rank)))
            repeat(3) { index -> assertEquals(if (rank == index + 1) 2 else 1, (input.deck ushr (index * 8)) and 0xff) }
            assertEquals(1 shl rank, input.tileset)
        }
        val lastCard = NneonneoInput.from(AiObservation.from(state.copy(deck = TileDeck(emptyList()), nextRank = 2)))
        assertEquals(1 shl 8, lastCard.deck)
    }

    @Test
    fun `bonus input hides the actual rank and retains the pre-move range`() {
        val board = Board.fromRanks(9, 9, 0, 0, 1, 2, 3, 0, 0, 3, 3, 0, 0, 0, 1, 2)
        val state = GameState(board, TileDeck(listOf(1, 2, 3)), 4)
        assertEquals(10, board.move(Direction.LEFT).board.highestRank())
        for (rank in 4..6) {
            val observation = AiObservation.from(state.copy(nextRank = rank))
            assertEquals(TilePreview.BONUS, observation.preview)
            assertEquals(listOf(4, 5, 6), observation.incomingRanks())
            val input = NneonneoInput.from(observation)
            assertEquals((1 shl 4) or (1 shl 5) or (1 shl 6), input.tileset)
            assertEquals(0x010101, input.deck)
        }
    }
}
