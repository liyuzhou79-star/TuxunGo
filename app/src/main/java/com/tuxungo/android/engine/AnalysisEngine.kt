package com.tuxungo.android.engine

import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Pure conversions between KataGo's side-to-move perspective and Black's. */
object AnalysisMath {
    /** Returns (winrate, scoreLead) from Black's perspective. */
    fun toBlackPerspective(winrate: Double, scoreLead: Double, sideToMove: Stone): Pair<Double, Double> {
        return if (sideToMove == Stone.BLACK) {
            winrate to scoreLead
        } else {
            (1.0 - winrate) to (-scoreLead)
        }
    }
}

data class Candidate(
    /** Null means the candidate is a pass. */
    val move: Move?,
    val vertex: String,
    val visits: Int,
    /** Win rate from Black's perspective, 0..1. */
    val winrateBlack: Double,
    /** Score lead from Black's perspective (positive = Black leads). */
    val scoreLeadBlack: Double,
    /** Principal variation as board moves, starting with this candidate. */
    val pvMoves: List<Move>,
    val pvText: String
)

data class AnalysisResult(
    val sideToMove: Stone,
    val rootWinrateBlack: Double,
    val rootScoreLeadBlack: Double,
    val candidates: List<Candidate>,
    /** Ownership per point, index = y * width + x, Black's perspective (+ = Black). */
    val ownershipBlack: DoubleArray
)

/**
 * Talks to a KataGo "analysis" process using its JSON query protocol.
 *
 * Protocol facts verified locally against KataGo v1.15.3 with the bundled
 * model (see project checkpoint): root and move win rates / score leads
 * are reported from the side-to-move perspective; the ownership array is
 * row-major from the top-left with POSITIVE meaning White owns the point
 * (negated here into Black's perspective); rectangular boards and
 * handicap initial stones are supported.
 *
 * All calls are blocking; use off the main thread. One query at a time.
 */
class AnalysisEngine private constructor(
    private val process: Process,
    private val input: BufferedWriter
) {
    @Volatile
    var isAlive: Boolean = true
        private set

    private val responses = LinkedBlockingQueue<JSONObject>()
    private val stderrTail = StringBuffer()
    private var nextId = 1

    private val readerThread = thread(start = true, isDaemon = true, name = "katago-analysis-reader") {
        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.startsWith("{")) {
                    try {
                        responses.put(JSONObject(trimmed))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        isAlive = false
    }

    private val stderrDrain = thread(start = true, isDaemon = true, name = "katago-analysis-stderr") {
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

    @Synchronized
    fun analyze(
        size: BoardSize,
        komi: Double,
        handicapStones: List<Pair<Int, Int>>,
        moves: List<Move>,
        sideToMove: Stone,
        maxVisits: Int,
        timeoutSeconds: Long = 120
    ): AnalysisResult {
        check(isAlive) { "分析引擎未运行。${diagnostics()}" }
        val id = "q${nextId++}"
        val query = JSONObject()
        query.put("id", id)
        query.put("rules", "chinese")
        query.put("komi", komi)
        query.put("boardXSize", size.width)
        query.put("boardYSize", size.height)
        query.put("maxVisits", maxVisits)
        query.put("includeOwnership", true)
        val initial = JSONArray()
        for ((x, y) in handicapStones) {
            val v = KataEngine.toVertex(Move(Stone.BLACK, x, y), size)
            initial.put(JSONArray().put("B").put(v))
        }
        query.put("initialStones", initial)
        query.put("initialPlayer", if (handicapStones.isNotEmpty()) "W" else "B")
        val moveArr = JSONArray()
        for (move in moves) {
            val color = if (move.stone == Stone.BLACK) "B" else "W"
            moveArr.put(JSONArray().put(color).put(KataEngine.toVertex(move, size)))
        }
        query.put("moves", moveArr)
        query.put("analyzeTurns", JSONArray().put(moves.size))

        // Drop any stale responses, then send.
        responses.clear()
        synchronized(input) {
            input.write(query.toString())
            input.newLine()
            input.flush()
        }
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                throw IllegalStateException("分析超时（${timeoutSeconds} 秒）。${diagnostics()}")
            }
            val resp = responses.poll(remaining, TimeUnit.MILLISECONDS)
                ?: throw IllegalStateException("分析超时（${timeoutSeconds} 秒）。${diagnostics()}")
            if (resp.optString("id") != id) continue
            if (resp.has("error")) {
                throw IllegalStateException("分析引擎报错：${resp.optString("error")} ${diagnostics()}")
            }
            if (resp.optBoolean("isDuringSearch", false)) continue
            return parseResult(resp, size, sideToMove)
        }
    }

    private fun parseResult(resp: JSONObject, size: BoardSize, sideToMove: Stone): AnalysisResult {
        val root = resp.optJSONObject("rootInfo")
        val rootWr = root?.optDouble("winrate", 0.5) ?: 0.5
        val rootLead = root?.optDouble("scoreLead", 0.0) ?: 0.0
        val (rootWrB, rootLeadB) = AnalysisMath.toBlackPerspective(rootWr, rootLead, sideToMove)

        val candidates = mutableListOf<Candidate>()
        val infos = resp.optJSONArray("moveInfos")
        if (infos != null) {
            val parsed = mutableListOf<Pair<Int, Candidate>>()
            for (i in 0 until infos.length()) {
                val info = infos.optJSONObject(i) ?: continue
                val vertex = info.optString("move", "pass")
                val move = if (vertex.equals("pass", ignoreCase = true)) {
                    null
                } else {
                    KataEngine.fromVertex(vertex, sideToMove, size)
                }
                if (!vertex.equals("pass", ignoreCase = true) && move == null) continue
                val wr = info.optDouble("winrate", 0.5)
                val lead = info.optDouble("scoreLead", 0.0)
                val (wrB, leadB) = AnalysisMath.toBlackPerspective(wr, lead, sideToMove)
                val pvArr = info.optJSONArray("pv")
                val pvMoves = mutableListOf<Move>()
                val pvTexts = mutableListOf<String>()
                if (pvArr != null) {
                    var pvStone = sideToMove
                    for (j in 0 until pvArr.length()) {
                        val pvVertex = pvArr.optString(j)
                        pvTexts.add(pvVertex)
                        if (!pvVertex.equals("pass", ignoreCase = true)) {
                            KataEngine.fromVertex(pvVertex, pvStone, size)?.let { pvMoves.add(it) }
                        }
                        pvStone = pvStone.other()
                    }
                }
                parsed.add(
                    info.optInt("order", i) to Candidate(
                        move = move,
                        vertex = vertex,
                        visits = info.optInt("visits", 0),
                        winrateBlack = wrB,
                        scoreLeadBlack = leadB,
                        pvMoves = pvMoves,
                        pvText = pvTexts.joinToString(" ")
                    )
                )
            }
            candidates.addAll(parsed.sortedBy { it.first }.take(5).map { it.second })
        }

        val ownership = DoubleArray(size.area)
        val ownArr = resp.optJSONArray("ownership")
        if (ownArr != null) {
            val n = minOf(ownArr.length(), size.area)
            for (i in 0 until n) {
                // KataGo reports positive = White; flip to Black's perspective.
                ownership[i] = -ownArr.optDouble(i, 0.0)
            }
        }
        return AnalysisResult(sideToMove, rootWrB, rootLeadB, candidates, ownership)
    }

    fun quit() {
        isAlive = false
        try {
            process.destroy()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun start(nativeLibDir: String, workDir: File, modelFile: File): AnalysisEngine {
            val binary = File(nativeLibDir, "libkatago.so")
            check(binary.exists()) { "KataGo binary missing: ${binary.absolutePath}" }
            check(modelFile.exists()) { "KataGo model missing: ${modelFile.absolutePath}" }
            val configFile = File(workDir, "analysis.cfg")
            // Key set verified locally against KataGo v1.15.3: analysis
            // mode aborts at startup when numAnalysisThreads is missing.
            configFile.writeText(
                """
                logFile = analysis.log
                numSearchThreads = 2
                nnMaxBatchSize = 8
                numAnalysisThreads = 1
                """.trimIndent() + "\n"
            )
            val process = ProcessBuilder(
                binary.absolutePath, "analysis",
                "-model", modelFile.absolutePath,
                "-config", configFile.absolutePath
            ).directory(workDir).start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val engine = AnalysisEngine(process, writer)
            // Readiness probe: an empty-board query with a single visit.
            try {
                engine.analyze(
                    size = BoardSize(9, 9),
                    komi = 7.5,
                    handicapStones = emptyList(),
                    moves = emptyList(),
                    sideToMove = Stone.BLACK,
                    maxVisits = 1,
                    timeoutSeconds = 60
                )
            } catch (e: Exception) {
                try {
                    process.waitFor(1, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
                val detail = engine.diagnostics()
                engine.quit()
                throw IllegalStateException(
                    "分析引擎启动失败：${e.message ?: "未知错误"}" +
                        if (detail.isNotEmpty()) "（$detail）" else ""
                )
            }
            return engine
        }
    }
}
