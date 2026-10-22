package com.zerocode.threes.core

import kotlin.random.Random

data class TileDeck(
    val lowCards: List<Int> = fullLowDeck(),
) {
    fun outcomes(highestRank: Int): List<DrawOutcome> {
        val available = if (lowCards.isEmpty()) fullLowDeck() else lowCards
        val bonusRanks = bonusRanks(highestRank)
        val basicWeight = if (bonusRanks.isEmpty()) 1.0 else BASIC_CARD_PROBABILITY

        val basicOutcomes = available
            .groupingBy { it }
            .eachCount()
            .map { (rank, count) ->
                val nextCards = available.toMutableList().also { it.remove(rank) }
                DrawOutcome(
                    rank = rank,
                    probability = basicWeight * count.toDouble() / available.size,
                    nextDeck = TileDeck(nextCards),
                )
            }

        if (bonusRanks.isEmpty()) return basicOutcomes
        val bonusProbability = (1.0 - BASIC_CARD_PROBABILITY) / bonusRanks.size
        return basicOutcomes + bonusRanks.map { rank ->
            DrawOutcome(rank, bonusProbability, TileDeck(available))
        }
    }

    fun draw(random: Random, highestRank: Int): DrawResult {
        val outcomes = outcomes(highestRank)
        val target = random.nextDouble()
        var cumulative = 0.0
        for (outcome in outcomes) {
            cumulative += outcome.probability
            if (target <= cumulative) return DrawResult(outcome.rank, outcome.nextDeck)
        }
        val fallback = outcomes.last()
        return DrawResult(fallback.rank, fallback.nextDeck)
    }

    companion object {
        private const val COPIES_PER_LOW_CARD = 4
        private const val BASIC_CARD_PROBABILITY = 20.0 / 21.0

        fun fullLowDeck(): List<Int> = buildList {
            repeat(COPIES_PER_LOW_CARD) {
                add(Board.ONE)
                add(Board.TWO)
                add(Board.THREE)
            }
        }

        fun bonusRanks(highestRank: Int): List<Int> {
            val maximumBonus = highestRank - 3
            return if (maximumBonus >= 4) (4..maximumBonus).toList() else emptyList()
        }
    }
}

data class DrawOutcome(
    val rank: Int,
    val probability: Double,
    val nextDeck: TileDeck,
)

data class DrawResult(
    val rank: Int,
    val nextDeck: TileDeck,
)
