package com.zerocode.threes.core

class Board private constructor(
    private val values: IntArray,
) {
    init {
        require(values.size == CELL_COUNT) { "A Threes board must contain $CELL_COUNT cells" }
        require(values.all { it in 0..MAX_RANK }) { "Tile ranks must be between 0 and $MAX_RANK" }
    }

    operator fun get(row: Int, column: Int): Int = values[index(row, column)]

    operator fun get(index: Int): Int = values[index]

    fun toIntArray(): IntArray = values.copyOf()

    fun emptyIndices(): List<Int> = values.indices.filter { values[it] == EMPTY }

    fun highestRank(): Int = values.maxOrNull() ?: EMPTY

    fun score(): Long = values.sumOf(::scoreForRank)

    fun withTile(index: Int, rank: Int): Board {
        require(index in values.indices)
        require(values[index] == EMPTY) { "Cannot spawn on an occupied cell" }
        require(rank in ONE..MAX_RANK)
        return Board(values.copyOf().also { it[index] = rank })
    }

    fun move(direction: Direction): BoardMove {
        val next = values.copyOf()
        val changedLines = linkedSetOf<Int>()
        val motions = mutableListOf<TileMotion>()
        var scoreGain = 0L

        for (lineIndex in 0 until SIZE) {
            val line = IntArray(SIZE) { offset ->
                val (row, column) = coordinates(direction, lineIndex, offset)
                values[index(row, column)]
            }
            val result = moveLineTowardStart(line)
            if (!result.changed) continue

            changedLines += lineIndex
            scoreGain += result.scoreGain
            result.motions.forEach { motion ->
                val (fromRow, fromColumn) = coordinates(direction, lineIndex, motion.fromOffset)
                val (toRow, toColumn) = coordinates(direction, lineIndex, motion.toOffset)
                motions += TileMotion(
                    fromIndex = index(fromRow, fromColumn),
                    toIndex = index(toRow, toColumn),
                    merges = motion.merges,
                )
            }
            for (offset in 0 until SIZE) {
                val (row, column) = coordinates(direction, lineIndex, offset)
                next[index(row, column)] = result.values[offset]
            }
        }

        return BoardMove(
            board = if (changedLines.isEmpty()) this else Board(next),
            direction = direction,
            changedLines = changedLines,
            motions = motions,
            scoreGain = scoreGain,
        )
    }

    fun legalDirections(): List<Direction> = Direction.entries.filter { move(it).moved }

    override fun equals(other: Any?): Boolean = other is Board && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = values.toList().chunked(SIZE).joinToString(separator = "\n")

    companion object {
        const val SIZE = 4
        const val CELL_COUNT = SIZE * SIZE
        const val EMPTY = 0
        const val ONE = 1
        const val TWO = 2
        const val THREE = 3
        const val MAX_RANK = 15

        fun empty(): Board = Board(IntArray(CELL_COUNT))

        fun fromRanks(vararg ranks: Int): Board = Board(ranks.copyOf())

        fun fromRanks(ranks: List<Int>): Board = Board(ranks.toIntArray())

        fun displayValue(rank: Int): Int = when (rank) {
            EMPTY -> 0
            ONE -> 1
            TWO -> 2
            else -> 3 shl (rank - THREE)
        }

        fun scoreForRank(rank: Int): Long {
            if (rank < THREE) return 0
            var result = 1L
            repeat(rank - TWO) { result *= 3L }
            return result
        }

        fun canMerge(first: Int, second: Int): Boolean = when {
            first == ONE && second == TWO -> true
            first == TWO && second == ONE -> true
            first >= THREE && first == second -> true
            else -> false
        }

        fun mergedRank(first: Int, second: Int): Int {
            require(canMerge(first, second)) { "Tiles $first and $second cannot merge" }
            return if (first <= TWO) THREE else (first + 1).coerceAtMost(MAX_RANK)
        }

        private fun index(row: Int, column: Int): Int {
            require(row in 0 until SIZE && column in 0 until SIZE)
            return row * SIZE + column
        }

        private fun coordinates(
            direction: Direction,
            line: Int,
            offset: Int,
        ): Pair<Int, Int> = when (direction) {
            Direction.LEFT -> line to offset
            Direction.RIGHT -> line to (SIZE - 1 - offset)
            Direction.UP -> offset to line
            Direction.DOWN -> (SIZE - 1 - offset) to line
        }

        private fun moveLineTowardStart(source: IntArray): LineMove {
            val result = source.copyOf()
            for (index in 0 until SIZE - 1) {
                val current = source[index]
                val following = source[index + 1]
                val canMove = current == EMPTY && following != EMPTY
                val canCombine = canMerge(current, following)
                if (!canMove && !canCombine) continue

                val newRank = if (canCombine) mergedRank(current, following) else following
                result[index] = newRank
                for (shift in index + 1 until SIZE - 1) {
                    result[shift] = source[shift + 1]
                }
                result[SIZE - 1] = EMPTY

                val motions = buildList {
                    if (canCombine) {
                        add(LineTileMotion(index + 1, index, merges = true))
                    } else {
                        add(LineTileMotion(index + 1, index, merges = false))
                    }
                    for (shift in index + 2 until SIZE) {
                        if (source[shift] != EMPTY) {
                            add(LineTileMotion(shift, shift - 1, merges = false))
                        }
                    }
                }

                val gain = if (canCombine) scoreForRank(newRank) else 0L
                return LineMove(result, changed = true, scoreGain = gain, motions = motions)
            }
            return LineMove(result, changed = false, scoreGain = 0L, motions = emptyList())
        }
    }
}

data class BoardMove(
    val board: Board,
    val direction: Direction,
    val changedLines: Set<Int>,
    val motions: List<TileMotion>,
    val scoreGain: Long,
) {
    val moved: Boolean get() = changedLines.isNotEmpty()

    fun spawnIndices(): List<Int> = changedLines.map { line ->
        when (direction) {
            Direction.LEFT -> line * Board.SIZE + Board.SIZE - 1
            Direction.RIGHT -> line * Board.SIZE
            Direction.UP -> (Board.SIZE - 1) * Board.SIZE + line
            Direction.DOWN -> line
        }
    }.filter { board[it] == Board.EMPTY }
}

data class TileMotion(
    val fromIndex: Int,
    val toIndex: Int,
    val merges: Boolean,
)

private data class LineMove(
    val values: IntArray,
    val changed: Boolean,
    val scoreGain: Long,
    val motions: List<LineTileMotion>,
)

private data class LineTileMotion(
    val fromOffset: Int,
    val toOffset: Int,
    val merges: Boolean,
)
