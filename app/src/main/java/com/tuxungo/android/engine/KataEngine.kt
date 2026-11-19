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

    private val stderrDrain = thread(start = true, isDaemon = true, name = "katago-stderr") {
        try {
            process.errorStream.bufferedReader().forEachLine { /* discard; keeps pipe from blocking */ }
        } catch (_: Exception) {
        }
    }

    /** Sends one GTP command and returns the response payload (without the leading '='). */
    @Synchronized
    fun command(cmd: String): String {
        check(isAlive) { "KataGo engine is not running" }
        val id = nextId++
        try {
            input.write("$id $cmd")
            input.newLine()
            input.flush()
            val sb = StringBuilder()
            while (true) {
                val line = output.readLine()
                    ?: throw IllegalStateException("KataGo engine closed its output")
                if (line.isEmpty()) break
                sb.append(line).append('\n')
            }
            val text = sb.toString().trim()
            if (text.startsWith("?")) {
                throw IllegalStateException("KataGo error for '$cmd': ${text.removePrefix("?").trim()}")
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

    fun play(move: Move, size: BoardSize) {
        val color = if (move.stone == Stone.BLACK) "black" else "white"
        command("play $color ${toVertex(move, size)}")
    }

    /** Asks the engine to generate a move for [stone]. Returns null on pass/resign. */
    fun genmove(stone: Stone, size: BoardSize): Move? {
        val color = if (stone == Stone.BLACK) "black" else "white"
        val vertex = command("genmove $color").trim().lowercase()
        if (vertex == "pass" || vertex == "resign") return null
        return fromVertex(vertex, stone, size)
    }

    fun quit() {
        try {
            command("quit")
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
        fun start(nativeLibDir: String, workDir: File, modelFile: File, maxVisits: Int): KataEngine {
            val binary = File(nativeLibDir, "libkatago.so")
            check(binary.exists()) { "KataGo binary missing: ${binary.absolutePath}" }
            check(modelFile.exists()) { "KataGo model missing: ${modelFile.absolutePath}" }
            val configFile = File(workDir, "gtp.cfg")
            configFile.writeText(
                """
                logFile = gtp.log
                logDir = .
                maxVisits = $maxVisits
                numSearchThreads = 2
                nnMaxBatchSize = 8
                ponderEnabled = false
                """.trimIndent() + "\n"
            )
            val process = ProcessBuilder(
                binary.absolutePath, "gtp",
                "-model", modelFile.absolutePath,
                "-config", configFile.absolutePath
            ).directory(workDir).start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val engine = KataEngine(process, writer, reader)
            // First command also serves as a readiness probe.
            engine.command("name")
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
