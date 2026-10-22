package com.zerocode.threes.ai.benchmark

import com.zerocode.threes.ai.AiPlayer
import com.zerocode.threes.ai.NneonneoInput
import com.zerocode.threes.ai.NneonneoTimedAi
import com.zerocode.threes.ai.SearchLimits
import com.zerocode.threes.core.Board
import com.zerocode.threes.core.GameEngine
import java.io.File
import java.io.Writer
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlin.random.Random

private data class Algorithm(val id: String, val depth: Int, val create: () -> AiPlayer)
private val algorithms = listOf(
    Algorithm("nneonneo-timed", 8) { NneonneoTimedAi() },
)

private data class Config(
    val games: Int, val seed: Int, val millis: Long, val nodes: Long, val maxMoves: Int,
    val maxGameSeconds: Long, val selected: List<Algorithm>, val output: File,
)

private data class Result(
    val algorithm: String, val seed: Int, val initialBoard: String, val status: String,
    val score: Long, val maxTile: Int, val moves: Int, val wallMillis: Double,
    val timings: List<Double>, val nodes: Long, val depth: Int, val error: String = "",
) {
    fun csv(): String = listOf(
        algorithm, seed, initialBoard, status, score, maxTile, moves, decimal(wallMillis),
        decimal(timings.averageOrZero()), decimal(percentile(timings, .50)),
        decimal(percentile(timings, .95)), decimal(timings.maxOrNull() ?: 0.0), nodes, depth, error,
    ).joinToString(",") { "\"${it.toString().replace("\"", "\"\"")}\"" }
}

fun main(args: Array<String>) {
    val options = args.associate {
        require(it.startsWith("--") && '=' in it) { "Expected --option=value: $it" }
        it.removePrefix("--").substringBefore('=') to it.substringAfter('=')
    }
    require(options.keys.all { it in setOf("games", "seed", "millis", "nodes", "max-moves", "max-game-seconds", "algorithms", "output") })
    val selected = options.getValue("algorithms").split(',').map { id ->
        algorithms.singleOrNull { it.id == id } ?: error("Unknown algorithm: $id")
    }
    require(selected.map { it.id }.distinct().size == selected.size)
    val config = Config(
        options.getValue("games").toInt(), options.getValue("seed").toInt(),
        options.getValue("millis").toLong(), options.getValue("nodes").toLong(),
        options.getValue("max-moves").toInt(), options.getValue("max-game-seconds").toLong(),
        selected, File(options.getValue("output")).absoluteFile,
    )
    require(config.games > 0 && config.millis > 0 && config.nodes > 0 && config.maxMoves > 0 && config.maxGameSeconds >= 0)
    config.output.mkdirs()
    // A new run never silently overwrites previous measurements.
    val csv = File(config.output, "games.csv")
    require(!csv.exists()) { "Results already exist at ${csv.path}; choose a new benchmarkOutput directory" }
    csv.writeText("algorithm,seed,initial_board,status,score,max_tile,moves,wall_ms,mean_move_ms,p50_move_ms,p95_move_ms,max_move_ms,searched_nodes,max_search_depth,error\n")
    File(config.output, "environment.txt").writeText(buildString {
        appendLine("started_utc=${Instant.now()}")
        appendLine("java=${System.getProperty("java.runtime.version")}")
        appendLine("vm=${System.getProperty("java.vm.name")}")
        appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
        appendLine("cpu=${System.getenv("PROCESSOR_IDENTIFIER") ?: "unknown"}")
        appendLine("processors=${Runtime.getRuntime().availableProcessors()}")
        appendLine("upstream=c554820ad848a73b7cd34e358c974ab2d85bd792")
        appendLine("arguments=${args.joinToString(" ")}")
        appendLine("schedule=sequential seeded games; AI does not read future random values")
        appendLine("policy=complete iterative deepening, original heuristic/cache/chance model; time/node limits; depth includes root")
        appendLine("warmup=12 opening searches per algorithm, excluded from measurements")
    })

    println("Headless AI benchmark: ${config.games} games; budget ${config.millis}ms; depth limit 8.")
    for (algorithm in selected) {
        val player = algorithm.create()
        repeat(12) { index ->
            player.chooseMove(GameEngine.newGame(Random(index)), SearchLimits(3, 10, 2_000))
        }
    }
    val results = mutableListOf<Result>()
    File(config.output, "moves.csv").bufferedWriter().use { moveLog ->
        moveLog.write("algorithm,seed,move,direction,score,max_tile,search_ms,completed_depth,searched_nodes\n")
        repeat(config.games) { gameIndex ->
            val seed = config.seed + gameIndex
            for (algorithm in selected) {
                println("START ${algorithm.id} seed=$seed")
                val result = play(algorithm, seed, config, moveLog)
                moveLog.flush()
                results += result
                csv.appendText(result.csv() + "\n")
                File(config.output, "summary.md").writeText(report(config, results))
                println("DONE ${result.algorithm} seed=$seed status=${result.status} score=${result.score} max=${result.maxTile} moves=${result.moves} mean=${decimal(result.timings.averageOrZero())}ms p95=${decimal(percentile(result.timings, .95))}ms wall=${decimal(result.wallMillis / 1_000)}s ${result.error}")
            }
        }
    }
    println("Results: ${File(config.output, "summary.md").path}")
    check(results.none { it.status == "error" }) { "Some games failed; see games.csv" }
}

private fun play(algorithm: Algorithm, seed: Int, config: Config, moveLog: Writer): Result {
    val environment = Random(seed)
    val player = algorithm.create()
    var game = GameEngine.newGame(environment)
    val initialBoard = java.lang.Long.toUnsignedString(NneonneoInput.pack(game.board), 16)
    val timings = mutableListOf<Double>()
    var nodes = 0L
    var depth = 0
    var error = ""
    var status = "game_over"
    val started = System.nanoTime()
    val deadline = if (config.maxGameSeconds == 0L) Long.MAX_VALUE else started + config.maxGameSeconds * 1_000_000_000
    var lastProgress = started
    try {
        while (!game.isGameOver) {
            if (game.moveCount >= config.maxMoves) { status = "move_limit"; break }
            if (System.nanoTime() >= deadline) { status = "time_limit"; break }
            val beforeSearch = System.nanoTime()
            val decision = try {
                checkNotNull(player.chooseMove(game, SearchLimits(
                    maxDepth = algorithm.depth,
                    timeBudgetMillis = config.millis,
                    maxNodes = config.nodes,
                    isCancelled = { System.nanoTime() >= deadline || Thread.currentThread().isInterrupted },
                ))) { "No decision for a nonterminal board" }
            } catch (cancelled: CancellationException) {
                if (System.nanoTime() >= deadline) { status = "time_limit"; break }
                throw cancelled
            }
            timings += (System.nanoTime() - beforeSearch) / 1_000_000.0
            check(decision.direction in game.legalDirections) { "Illegal move ${decision.direction}" }
            nodes += decision.searchedNodes
            depth = maxOf(depth, decision.completedDepth)
            game = checkNotNull(GameEngine.move(game, decision.direction, environment))
                .after
            moveLog.write("${algorithm.id},$seed,${game.moveCount},${decision.direction},${game.score},${Board.displayValue(game.board.highestRank())},${decimal(timings.last())},${decision.completedDepth},${decision.searchedNodes}\n")
            val now = System.nanoTime()
            if (now - lastProgress > 20_000_000_000) {
                println("PROGRESS ${algorithm.id} seed=$seed moves=${game.moveCount} score=${game.score} max=${Board.displayValue(game.board.highestRank())} elapsed=${decimal((now - started) / 1_000_000_000.0)}s")
                lastProgress = now
                moveLog.flush()
            }
        }
    } catch (failure: Exception) {
        status = "error"
        error = "${failure.javaClass.simpleName}: ${failure.message}"
    }
    return Result(
        algorithm.id, seed, initialBoard, status, game.score, Board.displayValue(game.board.highestRank()),
        game.moveCount, (System.nanoTime() - started) / 1_000_000.0, timings, nodes, depth, error,
    )
}

private fun report(config: Config, results: List<Result>): String = buildString {
    appendLine("# Threes AI 无界面测试")
    appendLine()
    appendLine("每种算法计划 ${config.games} 局，起始种子 ${config.seed}；共完成 ${results.size}/${config.games * config.selected.size} 条记录。")
    appendLine("仅使用 nneonneo 限时版，界面名称为 AI 模式。每步 ${config.millis}ms、最多 ${config.nodes} 节点、深度上限 8（含根移动），完整完成一层才更新结果。")
    appendLine("时间限制为协作式，完整的一步保底允许轻微超时。桌面结果不等同于手机性能。")
    appendLine()
    appendLine("| 算法 | 结束/已跑 | 平均终局分 | 中位终局分 | 最高终局分 | 最高方块 | ≥384 | ≥1536 | ≥6144 | 平均步时 ms | P95 步时 ms |")
    appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    for (algorithm in config.selected) {
        val attempted = results.filter { it.algorithm == algorithm.id }
        val complete = attempted.filter { it.status == "game_over" }
        val times = complete.flatMap { it.timings }
        fun number(value: Double) = if (complete.isEmpty()) "—" else decimal(value)
        fun rate(tile: Int) = if (complete.isEmpty()) "—" else "${decimal(100.0 * complete.count { it.maxTile >= tile } / complete.size)}%"
        appendLine("| ${algorithm.id} | ${complete.size}/${attempted.size} | ${number(complete.map { it.score.toDouble() }.averageOrZero())} | ${number(percentile(complete.map { it.score.toDouble() }, .5))} | ${complete.maxOfOrNull { it.score } ?: "—"} | ${complete.maxOfOrNull { it.maxTile } ?: "—"} | ${rate(384)} | ${rate(1536)} | ${rate(6144)} | ${number(times.averageOrZero())} | ${number(percentile(times, .95))} |")
    }
    val incomplete = results.filter { it.status != "game_over" }
    if (incomplete.isNotEmpty()) {
        appendLine()
        appendLine("## 未结束或失败的记录（不混入终局均分和达成率）")
        appendLine()
        appendLine("| 算法 | 种子 | 状态 | 当前分 | 当前最高方块 | 步数 | 耗时秒 |")
        appendLine("| --- | ---: | --- | ---: | ---: | ---: | ---: |")
        incomplete.forEach {
            appendLine("| ${it.algorithm} | ${it.seed} | ${it.status} | ${it.score} | ${it.maxTile} | ${it.moves} | ${decimal(it.wallMillis / 1_000)} |")
        }
    }
    appendLine()
    appendLine("## 复现与解释")
    appendLine()
    appendLine("- 使用固定种子生成开局和出牌；AI 不读取未来随机数或奖励牌隐藏值。")
    appendLine("- 使用与 App 相同的 GameEngine 和计分方式（棋盘方块分值之和）；未知奖励牌仅提供可见候选范围。")
    appendLine("- 计时前预热 12 次，正式对局串行运行。")
    appendLine("- 固定种子记录见 games.csv，逐步时间/深度见 moves.csv，机器和参数见 environment.txt。时间预算会受 JIT、GC 和系统负载影响，固定种子不保证时间预算算法逐步完全一致。")
    appendLine("- P95 按全部已结束对局的实际单步耗时计算，包含 JNI 边界，排除预热。")
    appendLine("- 少量样本只支持初步判断，最高分不能代替稳定胜率。未结束对局的分数只是当前值，不能当作终局成绩。")
    appendLine("- 固定上游提交 c554820ad848a73b7cd34e358c974ab2d85bd792，保留评分、查表、缓存与随机出牌模型；采用限时逐层加深，超时丢弃整层。节点数包含丢弃层。")
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
private fun percentile(values: List<Double>, quantile: Double): Double =
    if (values.isEmpty()) 0.0 else values.sorted()[(ceil(values.size * quantile).toInt() - 1).coerceAtLeast(0)]
private fun decimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
