package com.tuxungo.android.engine

import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Talks to the bundled KataGo executable over GTP (stdin/stdout).
 *
 * The engine binary ships as jniLibs/arm64-v8a/libkatago.so so Android
 * extracts it into nativeLibraryDir, the one app-private location from
 * which executing a bundled binary is allowed on Android 10+.
 *
 * All calls are blocking and run on a single background thread owned by
 * this class; callers must not invoke them on the main thread.
 */
class KataEngine private constructor(
    private val process: Process,
    private val input: BufferedWriter,
    private val output: BufferedReader
) {
    @Volatile
    private var nextId = 1

    @Volatile
    var isAlive: Boolean = true
        private set

    /** Last stderr lines from the engine, kept so failures can show the real cause. */
    private val stderrTail = StringBuffer()

    /** Complete GTP responses (each ends with a blank line), in order. */
    private val responseBlocks = java.util.concurrent.LinkedBlockingQueue<String>()

    private val readerThread = thread(start = true, isDaemon = true, name = "katago-gtp-reader") {
        try {
            val sb = StringBuilder()
            while (true) {
                val line = output.readLine() ?: break
                if (line.isEmpty()) {
                    responseBlocks.put(sb.toString())
                    sb.setLength(0)
                } else {
                    sb.append(line).append('\n')
                }
            }
        } catch (_: Exception) {
        }
        isAlive = false
    }

    private val stderrDrain = thread(start = true, isDaemon = true, name = "katago-stderr") {
        try {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(stderrTail) {
                    stderrTail.append(line).append('\n')
                    if (stderrTail.length > 4000) {
                        stderrTail.delete(0, stderrTail.length - 4000)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Exit code (if the process already ended) plus the engine's own last words. */
    fun diagnostics(): String {
        val sb = StringBuilder()
        if (!process.isAlive) {
            try {
                sb.append("引擎进程已退出，退出码 ").append(process.exitValue()).append("。")
            } catch (_: Exception) {
            }
        }
        val tail = synchronized(stderrTail) { stderrTail.toString() }.trim()
        if (tail.isNotEmpty()) {
            sb.append("引擎报错：").append(tail.lines().takeLast(4).joinToString(" / "))
        }
        return sb.toString()
    }

    /**
     * Sends one GTP command and returns the response payload (without
     * the leading '='). The wait is sliced so a dead engine fails at
     * once and a wedged engine hits [timeoutSeconds] instead of
     * hanging the caller forever; a timeout kills the process so the
     * failure path can rebuild it.
     */
    @Synchronized
    fun command(cmd: String, timeoutSeconds: Long = 240): String {
        check(isAlive) { "KataGo engine is not running. ${diagnostics()}" }
        val id = nextId++
        try {
            synchronized(input) {
                input.write("$id $cmd")
                input.newLine()
                input.flush()
            }
            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
            var block: String? = null
            while (block == null) {
                if (!process.isAlive) {
                    isAlive = false
                    throw IllegalStateException(
                        "KataGo engine closed its output. ${diagnostics()}"
                    )
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    isAlive = false
                    try {
                        process.destroy()
                    } catch (_: Exception) {
                    }
                    throw IllegalStateException(
                        "引擎无响应（${timeoutSeconds} 秒超时）。${diagnostics()}"
                    )
                }
                block = responseBlocks.poll(
                    minOf(500L, remaining), TimeUnit.MILLISECONDS
                )
            }
            val text = block.trim()
            if (text.startsWith("?")) {
                throw IllegalStateException(
                    "KataGo error for '$cmd': ${text.removePrefix("?").trim()} ${diagnostics()}"
                )
            }
            check(text.startsWith("=")) { "Unexpected KataGo response: $text" }
            // Strip "=id" prefix.
            val body = text.removePrefix("=")
            val space = body.indexOf(' ')
            val payload = if (space >= 0 && body.substring(0, space).toIntOrNull() == id) {
                body.substring(space + 1)
            } else body
            return payload.trim()
        } catch (e: Exception) {
            isAlive = false
            throw e
        }
    }

    fun setBoardSize(size: BoardSize) {
        if (size.isSquare) command("boardsize ${size.width}")
        else command("rectangular_boardsize ${size.width} ${size.height}")
    }

    fun setKomi(komi: Double) {
        command("komi $komi")
    }

    fun clearBoard() {
        command("clear_board")
    }

    fun setMaxVisits(visits: Int) {
        command("kata-set-param maxVisits $visits")
    }

    /**
     * Sets an arbitrary position with KataGo's set_position command
     * (space-separated COLOR VERTEX pairs). Throws when KataGo rejects
     * the placement (overlap or a group without liberties).
     */
    fun setPosition(stones: List<Move>, size: BoardSize) {
        if (stones.isEmpty()) return
        val sb = StringBuilder("set_position")
        for (s in stones) {
            sb.append(' ').append(if (s.stone == Stone.BLACK) "B" else "W")
            sb.append(' ').append(toVertex(s, size))
        }
        command(sb.toString())
    }

    fun play(move: Move, size: BoardSize) {
        val color = if (move.stone == Stone.BLACK) "black" else "white"
        command("play $color ${toVertex(move, size)}")
    }

    /** Whether the last [genmove] answer was "resign" (vs "pass"):
     *  both return null, but a resign is an AI resign proposal the
     *  UI must ask the user about, not a pass. */
    var lastGenmoveResigned: Boolean = false
        private set

    /** Asks the engine to generate a move for [stone]. Returns null on pass/resign. */
    fun genmove(stone: Stone, size: BoardSize): Move? {
        val color = if (stone == Stone.BLACK) "black" else "white"
        val vertex = command("genmove $color").trim().lowercase()
        lastGenmoveResigned = vertex == "resign"
        if (vertex == "pass" || vertex == "resign") return null
        return fromVertex(vertex, stone, size)
    }

    fun quit() {
        try {
            command("quit", timeoutSeconds = 5)
        } catch (_: Exception) {
        }
        isAlive = false
        try {
            process.waitFor(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
        process.destroy()
    }

    companion object {
        private const val GTP_LETTERS = "ABCDEFGHJKLMNOPQRSTUVWXYZ" // GTP skips only I

        /** GTP rows count from the bottom; our y counts from the top. */
        fun toVertex(move: Move, size: BoardSize): String {
            if (move.isPass) return "pass"
            val col = GTP_LETTERS[move.x]
            val row = size.height - move.y
            return "$col$row"
        }

        fun fromVertex(vertex: String, stone: Stone, size: BoardSize): Move? {
            val v = vertex.trim().uppercase()
            if (v == "PASS" || v.isEmpty()) return null
            val col = GTP_LETTERS.indexOf(v[0])
            val row = v.substring(1).toIntOrNull() ?: return null
            if (col < 0) return null
            val y = size.height - row
            val x = col
            if (x !in 0 until size.width || y !in 0 until size.height) return null
            return Move(stone, x, y)
        }

        /**
         * Starts KataGo. [nativeLibDir] is Context.nativeLibraryDir;
         * [workDir] must be app-private writable storage (config + logs live there).
         */
        fun start(
            nativeLibDir: String, workDir: File, modelFile: File,
            maxVisits: Int, humanModelFile: File? = null,
            rankProfile: String? = null
        ): KataEngine {
            val binary = File(nativeLibDir, "libkatago.so")
            check(binary.exists()) { "KataGo binary missing: ${binary.absolutePath}" }
            check(modelFile.exists()) { "KataGo model missing: ${modelFile.absolutePath}" }
            if (humanModelFile != null) {
                check(humanModelFile.exists()) {
                    "KataGo human model missing: ${humanModelFile.absolutePath}"
                }
            }
            val configFile = File(workDir, "gtp.cfg")
            // This key set is verified against KataGo v1.15.3 by running it
            // locally: KataGo aborts at startup when logFile and logDir are
            // both present, and when any of logAllGTPCommunication,
            // logSearchInfo, koRule, scoringRule or multiStoneSuicideLegal
            // is missing. Do not trim keys casually.
            // Two human-model modes share this key set (verified
            // locally against v1.15.3 in both): "human-like" blends
            // the top human profile with a strong-search veto
            // (PiklLambda 0.08, never plays a blunder on purpose),
            // while a rank profile plays PURE imitation at that
            // rank: PiklLambda huge means the search cannot rescue
            // the move choice, and the move is SAMPLED from the
            // rank's human policy under the temperature keys below.
            // The temperature keys are the real strength dial for a
            // ranked side. KataGo's own weak-human example config
            // (gtp_human5k_example.cfg) warns that a low temperature
            // imitates the "majority vote" of the rank instead of an
            // individual player: the majority rarely picks any given
            // blunder, so the bot plays far above its nominal rank.
            // Verified locally at rank_17k: the old keys
            // (0.70/0.25/30/1.0) picked the same move in 8/8 samples,
            // while the example config's keys (0.85/0.70/80/0.01)
            // spread 8 samples over 7 distinct plausible moves.
            // Ranked sides therefore use the example values; the
            // blend mode keeps its sharper keys (its search veto is
            // what holds its strength, not the sampling).
            val humanKeys = if (humanModelFile == null) "" else """
                humanSLProfile = ${rankProfile ?: "rank_9d"}
                humanSLChosenMoveProp = 1.0
                humanSLChosenMoveIgnorePass = true
                humanSLChosenMovePiklLambda = ${if (rankProfile != null) "100000000" else "0.08"}
                humanSLRootExploreProbWeightless = 0.8
                humanSLCpuctExploration = 0.50
                humanSLCpuctPermanent = 2.0
                chosenMoveTemperatureEarly = ${if (rankProfile != null) "0.85" else "0.70"}
                chosenMoveTemperature = ${if (rankProfile != null) "0.70" else "0.25"}
                chosenMoveTemperatureHalflife = ${if (rankProfile != null) "80" else "30"}
                chosenMoveTemperatureOnlyBelowProb = ${if (rankProfile != null) "0.01" else "1.0"}
                useLcbForSelection = false
                useUncertainty = false
                subtreeValueBiasFactor = 0.0
                winLossUtilityFactor = 1.0
                staticScoreUtilityFactor = 0.30
                dynamicScoreUtilityFactor = 0.00
                ignorePreRootHistory = false
                rootNumSymmetriesToSample = 2
                """.trimIndent() + "\n"
            configFile.writeText(
                """
                logFile = gtp.log
                logAllGTPCommunication = false
                logSearchInfo = false
                koRule = POSITIONAL
                scoringRule = AREA
                multiStoneSuicideLegal = false
                maxVisits = $maxVisits
                numSearchThreads = 4
                nnMaxBatchSize = 16
                ponderingEnabled = false
                """.trimIndent() + "\n" + humanKeys
            )
            val args = mutableListOf(
                binary.absolutePath, "gtp",
                "-model", modelFile.absolutePath,
                "-config", configFile.absolutePath
            )
            if (humanModelFile != null) {
                args += "-human-model"
                args += humanModelFile.absolutePath
            }
            val process = ProcessBuilder(args).directory(workDir).start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val engine = KataEngine(process, writer, reader)
            // First command also serves as a readiness probe. On failure,
            // give the process a moment to finish dying so the exception
            // can carry its exit code and stderr.
            try {
                engine.command("name")
            } catch (e: Exception) {
                try {
                    process.waitFor(1, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
                val detail = engine.diagnostics()
                throw IllegalStateException(
                    "KataGo 启动失败：${e.message ?: "未知错误"}" +
                        if (detail.isNotEmpty()) "（$detail）" else ""
                )
            }
            return engine
        }
    }
}

/** Runs engine calls off the main thread; callbacks arrive on the main thread. */
class EngineRunner {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "katago-runner").apply { isDaemon = true }
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun <T> submit(task: () -> T, onDone: (Result<T>) -> Unit) {
        executor.execute {
            val result = runCatching(task)
            mainHandler.post { onDone(result) }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
