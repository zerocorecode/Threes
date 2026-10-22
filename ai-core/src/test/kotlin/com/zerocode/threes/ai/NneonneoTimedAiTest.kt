package com.zerocode.threes.ai

import com.zerocode.threes.core.Board
import com.zerocode.threes.core.GameEngine
import com.zerocode.threes.core.GameState
import com.zerocode.threes.core.TileDeck
import java.util.concurrent.CancellationException
import java.util.function.BooleanSupplier
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NneonneoTimedAiTest {
    @Test
    fun `fixed completed depths match independent pristine source scores`() {
        for (seed in listOf(17, 29)) {
            val input = NneonneoInput.from(AiObservation.from(GameEngine.newGame(Random(seed))))
            for (depth in 1..3) {
                val original = NneonneoNative.originalScoresAtDepth(input.board, input.deck, input.tileset, depth)
                val timed = NneonneoNative.evaluateTimed(input.board, input.deck, input.tileset,
                    depth, 10_000_000_000, Long.MAX_VALUE, BooleanSupplier { false })
                assertEquals(depth.toDouble(), timed[6])
                assertContentEquals(original, timed.copyOf(4))
                var best = 0.0
                var chosen = -1
                original.forEachIndexed { index, value -> if (value > best) { best = value; chosen = index } }
                assertEquals(chosen.toDouble(), timed[4])
            }
        }
    }

    @Test
    fun `exhausted node budgets publish a whole previous round`() {
        val state = GameEngine.newGame(Random(17))
        val input = NneonneoInput.from(AiObservation.from(state))
        for (nodes in listOf(1L, 10L, 100L, 1_000L)) {
            val timed = NneonneoNative.evaluateTimed(input.board, input.deck, input.tileset,
                8, 10_000_000_000, nodes, BooleanSupplier { false })
            val depth = timed[6].toInt()
            assertTrue(depth in 1..7)
            if (nodes == 1L) assertEquals(1, depth)
            val original = NneonneoNative.originalScoresAtDepth(input.board, input.deck, input.tileset, depth)
            assertContentEquals(original, timed.copyOf(4), "Partial root results must be discarded")
        }
    }

    @Test
    fun `small time budget interrupts expensive search with a legal fallback`() {
        val player = NneonneoTimedAi()
        player.chooseMove(GameEngine.newGame(Random(17)), SearchLimits(1, 10, Long.MAX_VALUE))
        val state = GameState(Board.fromRanks(3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 0, 1, 2, 3), TileDeck(), 1)
        val started = System.nanoTime()
        val decision = assertNotNull(player.chooseMove(state, SearchLimits(12, 5, Long.MAX_VALUE)))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "Cooperative deadline must stop expensive work")
        assertTrue(decision.completedDepth in 1..11)
        assertTrue(decision.direction in state.legalDirections)
        assertEquals(state.legalDirections.toSet(), decision.alternatives.filter { it.isLegal }.map { it.direction }.toSet())
    }

    @Test
    fun `explicit cancellation propagates instead of returning a stale fallback`() {
        val player = NneonneoTimedAi()
        var checks = 0
        assertFailsWith<CancellationException> {
            player.chooseMove(GameEngine.newGame(Random(17)), SearchLimits(12, 10_000,
                Long.MAX_VALUE, isCancelled = { ++checks >= 8 }))
        }
        assertTrue(checks >= 8)
        assertNotNull(player.chooseMove(GameEngine.newGame(Random(29)), SearchLimits(1, 10, 1)))
    }

    @Test
    fun `bonus hidden values remain indistinguishable in timed mode`() {
        val state = GameState(Board.fromRanks(9, 9, 0, 0, 1, 2, 3, 0, 0, 3, 3, 0, 0, 0, 1, 2),
            TileDeck(listOf(1, 2, 3)), 4)
        val player = NneonneoTimedAi()
        val expected = assertNotNull(player.chooseMove(state, SearchLimits(1, 10, 1)))
        for (rank in 5..6) {
            val actual = assertNotNull(player.chooseMove(state.copy(nextRank = rank), SearchLimits(1, 10, 1)))
            assertEquals(expected.alternatives, actual.alternatives)
            assertEquals(expected.direction, actual.direction)
        }
    }

    @Test
    fun `terminal board has no timed decision`() {
        assertNull(NneonneoTimedAi().chooseMove(GameState(Board.fromRanks(List(16) { 1 }), TileDeck(), 2)))
    }
}
