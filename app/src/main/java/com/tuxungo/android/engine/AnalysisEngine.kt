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

    /**
     * KataGo's ownership array is from the SIDE-TO-MOVE perspective
     * (positive = the player to move owns the point) — verified locally
     * with corner enclosures run under both Black-to-move and
     * White-to-move (drive5/drive6): the signs flip with the side to
     * move. Convert to Black's perspective (positive = Black owns).
     */
    fun ownershipToBlack(raw: DoubleArray, sideToMove: Stone): DoubleArray {
        if (sideToMove == Stone.BLACK) return raw.copyOf()
        return DoubleArray(raw.size) { i -> -raw[i] }
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
    val ownershipBlack: DoubleArray,
    /** The human-style model's favourite move for this position (null
     *  when it prefers to pass, or when no human model is loaded). */
    val humanTopMove: Move? = null,
    /** GTP vertex of the human favourite ("pass" included), "" when
     *  no human model is loaded. */
    val humanTopVertex: String = "",
    /** How likely a top human player is to play that move, 0..1. */
    val humanTopProb: Double = 0.0
)

/**
 * Talks to a KataGo "analysis" process using its JSON query protocol.
 *
 * Protocol facts verified locally against KataGo v1.15.3 with the bundled
 * model (see project checkpoint): root and move win rates / score leads
 * are reported from the side-to-move perspective; the ownership array is
 * ALSO side-to-move perspective (positive = the player to move owns the
 * point), row-major from the top-left. Win rates, score leads and
 * ownership are all converted to Black's perspective in [parse].
 * (verified with asymmetric corner enclosures and on-stone values);
 * rectangular boards and handicap initial stones are supported.
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

    /** True when a human-style model is loaded alongside: analyze()
     *  then also asks for the human policy (verified locally: the
     *  query needs includePolicy plus a humanSLProfile override, and
     *  the reply carries a top-level "humanPolicy" array in the same
     *  row-major-from-the-top layout as "policy"). */
    var humanEnabled: Boolean = false

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
        initialStones: List<Move>,
        initialPlayer: Stone,
        moves: List<Move>,
        sideToMove: Stone,
        maxVisits: Int,
        timeoutSeconds: Long = 120,
        onProvisional: ((AnalysisResult) -> Unit)? = null
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
        query.put("includePolicy", true)
        if (onProvisional != null) {
            // Stream partial results while the search runs, so the UI
            // can show a provisional top move almost immediately and
            // refine it (verified locally against v1.15.3: replies
            // with isDuringSearch=true carry the same shape).
            query.put("reportDuringSearchEvery", 0.3)
        }
        if (humanEnabled) {
            query.put(
                "overrideSettings",
                JSONObject().put("humanSLProfile", "rank_9d")
            )
        }
        val initial = JSONArray()
        for (s in initialStones) {
            val v = KataEngine.toVertex(s, size)
            initial.put(JSONArray().put(if (s.stone == Stone.BLACK) "B" else "W").put(v))
        }
        query.put("initialStones", initial)
        query.put("initialPlayer", if (initialPlayer == Stone.BLACK) "B" else "W")
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
            // Sliced polling: notice a dead engine within half a
            // second instead of sleeping through the whole timeout.
            if (!process.isAlive) {
                isAlive = false
                throw IllegalStateException("分析引擎已退出。${diagnostics()}")
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                killWedged()
                throw IllegalStateException("分析超时（${timeoutSeconds} 秒）。${diagnostics()}")
            }
            val resp = responses.poll(minOf(500L, remaining), TimeUnit.MILLISECONDS)
                ?: continue
            if (resp.optString("id") != id) continue
            if (resp.has("error")) {
                throw IllegalStateException("分析引擎报错：${resp.optString("error")} ${diagnostics()}")
            }
            if (resp.optBoolean("isDuringSearch", false)) {
                if (onProvisional != null) {
                    try {
                        onProvisional(parseResult(resp, size, sideToMove))
                    } catch (_: Exception) {
                    }
                }
                continue
            }
            return parseResult(resp, size, sideToMove)
        }
    }

    /** A wedged engine is destroyed so the next task builds a fresh one. */
    private fun killWedged() {
        isAlive = false
        try {
            process.destroy()
        } catch (_: Exception) {
        }
    }

    /**
     * Analyzes EVERY turn of a finished game in one query (for the
     * review screen). Returns one TurnStat per turn 0..moves.size.
     * [onProgress] is called with (done, total) as turns stream in.
     */
    @Synchronized
    fun analyzeGame(
        size: BoardSize,
        komi: Double,
        initialStones: List<Move>,
        initialPlayer: Stone,
        moves: List<Move>,
        sideToMove: Stone,
        maxVisits: Int,
        timeoutSeconds: Long = 900,
        onlyTurns: Set<Int>? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<TurnStat> {
        check(isAlive) { "分析引擎未运行。${diagnostics()}" }
        val id = "q${nextId++}"
        val query = JSONObject()
        query.put("id", id)
        query.put("rules", "chinese")
        query.put("komi", komi)
        query.put("boardXSize", size.width)
        query.put("boardYSize", size.height)
        query.put("maxVisits", maxVisits)
        query.put("includeOwnership", false)
        val initial = JSONArray()
        for (s in initialStones) {
            val v = KataEngine.toVertex(s, size)
            initial.put(JSONArray().put(if (s.stone == Stone.BLACK) "B" else "W").put(v))
        }
        query.put("initialStones", initial)
        query.put("initialPlayer", if (initialPlayer == Stone.BLACK) "B" else "W")
        val moveArr = JSONArray()
        for (move in moves) {
            val color = if (move.stone == Stone.BLACK) "B" else "W"
            moveArr.put(JSONArray().put(color).put(KataEngine.toVertex(move, size)))
        }
        query.put("moves", moveArr)
        val wantedTurns = onlyTurns?.sorted() ?: (0..moves.size).toList()
        val turnsArr = JSONArray()
        for (t in wantedTurns) turnsArr.put(t)
        query.put("analyzeTurns", turnsArr)

        responses.clear()
        synchronized(input) {
            input.write(query.toString())
            input.newLine()
            input.flush()
        }
        val total = wantedTurns.size
        val byTurn = HashMap<Int, TurnStat>()
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        while (byTurn.size < total) {
            if (!process.isAlive) {
                isAlive = false
                throw IllegalStateException("分析引擎已退出。${diagnostics()}")
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                killWedged()
                throw IllegalStateException("整场分析超时。${diagnostics()}")
            }
            val resp = responses.poll(minOf(500L, remaining), TimeUnit.MILLISECONDS)
                ?: continue
            if (resp.optString("id") != id) continue
            if (resp.has("error")) {
                throw IllegalStateException("分析引擎报错：${resp.optString("error")} ${diagnostics()}")
            }
            if (resp.optBoolean("isDuringSearch", false)) continue
            val turn = resp.optInt("turnNumber", -1)
            if (turn < 0 || byTurn.containsKey(turn)) continue
            val mover = if (turn < moves.size) moves[turn].stone else sideToMove
            val root = resp.optJSONObject("rootInfo")
            val rootWr = root?.optDouble("winrate", 0.5) ?: 0.5
            val rootLead = root?.optDouble("scoreLead", 0.0) ?: 0.0
            val (wrB, leadB) = AnalysisMath.toBlackPerspective(rootWr, rootLead, mover)
            var best: Move? = null
            var bestIsPass = false
            var bestGap = 0.0
            var bestPv = ""
            val infos = resp.optJSONArray("moveInfos")
            if (infos != null && infos.length() > 0) {
                var bestOrder = Int.MAX_VALUE
                val byOrder = mutableListOf<Pair<Int, Double>>()
                for (i in 0 until infos.length()) {
                    val info = infos.optJSONObject(i) ?: continue
                    val order = info.optInt("order", Int.MAX_VALUE)
                    byOrder.add(order to info.optDouble("winrate", 0.5))
                    if (order < bestOrder) {
                        bestOrder = order
                        val vertex = info.optString("move", "pass")
                        if (vertex.equals("pass", ignoreCase = true)) {
                            bestIsPass = true
                            best = null
                        } else {
                            best = KataEngine.fromVertex(vertex, mover, size)
                            bestIsPass = false
                        }
                        val pvArr = info.optJSONArray("pv")
                        bestPv = if (pvArr != null) {
                            (0 until pvArr.length()).joinToString(" ") {
                                pvArr.optString(it)
                            }
                        } else {
                            ""
                        }
                    }
                }
                // How much the top choice stood out from the runner-up
                // (both win rates share the side-to-move perspective,
                // so the gap is perspective-free). The review uses it
                // to keep "brilliant" marks meaningful.
                val sorted = byOrder.sortedBy { it.first }
                if (sorted.size >= 2) {
                    bestGap = (sorted[0].second - sorted[1].second).coerceAtLeast(0.0)
                }
            }
            byTurn[turn] = TurnStat(turn, wrB, leadB, best, bestIsPass, bestGap, bestPv)
            onProgress(byTurn.size, total)
        }
        return wantedTurns.map { byTurn[it] ?: TurnStat(it, 0.5, 0.0, null, false) }
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

        val rawOwnership = DoubleArray(size.area)
        val ownArr = resp.optJSONArray("ownership")
        if (ownArr != null) {
            val n = minOf(ownArr.length(), size.area)
            for (i in 0 until n) {
                rawOwnership[i] = ownArr.optDouble(i, 0.0)
            }
        }
        // KataGo ownership is side-to-move perspective; store Black's.
        val ownership = AnalysisMath.ownershipToBlack(rawOwnership, sideToMove)

        // Human-style favourite: argmax of the human policy array
        // (board points in row-major order from the top, pass last).
        var humanTopMove: Move? = null
        var humanTopVertex = ""
        var humanTopProb = 0.0
        val humanArr = resp.optJSONArray("humanPolicy")
        if (humanArr != null && humanArr.length() >= size.area) {
            var bestIdx = -1
            var bestVal = 0.0
            for (i in 0 until size.area) {
                val v = humanArr.optDouble(i, 0.0)
                if (v > bestVal) {
                    bestVal = v
                    bestIdx = i
                }
            }
            val passProb = if (humanArr.length() > size.area) {
                humanArr.optDouble(size.area, 0.0)
            } else {
                0.0
            }
            if (bestIdx >= 0 && bestVal >= passProb && bestVal > 0.001) {
                val move = Move(
                    sideToMove,
                    bestIdx % size.width,
                    bestIdx / size.width
                )
                humanTopMove = move
                humanTopVertex = KataEngine.toVertex(move, size)
                humanTopProb = bestVal
            } else if (passProb > 0.001) {
                humanTopVertex = "pass"
                humanTopProb = passProb
            }
        }
        return AnalysisResult(
            sideToMove, rootWrB, rootLeadB, candidates, ownership,
            humanTopMove, humanTopVertex, humanTopProb
        )
    }

    fun quit() {
        isAlive = false
        try {
            process.destroy()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun start(
            nativeLibDir: String,
            workDir: File,
            modelFile: File,
            humanModelFile: File? = null
        ): AnalysisEngine {
            val binary = File(nativeLibDir, "libkatago.so")
            check(binary.exists()) { "KataGo binary missing: ${binary.absolutePath}" }
            check(modelFile.exists()) { "KataGo model missing: ${modelFile.absolutePath}" }
            val configFile = File(workDir, "analysis.cfg")
            // Key set verified locally against KataGo v1.15.3: analysis
            // mode aborts at startup when numAnalysisThreads is missing.
            configFile.writeText(
                """
                logFile = analysis.log
                numSearchThreads = 6
                nnMaxBatchSize = 16
                numAnalysisThreads = 1
                """.trimIndent() + "\n"
            )
            val args = mutableListOf(
                binary.absolutePath, "analysis",
                "-model", modelFile.absolutePath,
                "-config", configFile.absolutePath
            )
            val humanOk = humanModelFile != null && humanModelFile.exists()
            if (humanOk) {
                args.add("-human-model")
                args.add(humanModelFile.absolutePath)
            }
            val process = ProcessBuilder(args).directory(workDir).start()
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream))
            val engine = AnalysisEngine(process, writer)
            engine.humanEnabled = humanOk
            // Readiness probe: an empty-board query with a single visit.
            try {
                engine.analyze(
                    size = BoardSize(9, 9),
                    komi = 7.5,
                    initialStones = emptyList(),
                    initialPlayer = Stone.BLACK,
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

/**
 * Holds one idle, pre-warmed analysis engine so the first
 * analysis of a session does not pay the model-load cost.
 * MainActivity warms it shortly after launch; the game screen
 * adopts it via [take] (and owns it from then on).
 */
object AnalysisEngineHolder {
    @Volatile
    private var held: AnalysisEngine? = null

    /** Stores a warmed engine, retiring any previous one. */
    fun offer(engine: AnalysisEngine) {
        val old = held
        held = engine
        if (old != null && old !== engine) {
            runCatching { old.quit() }
        }
    }

    /** Takes the warmed engine out (single use), or null. */
    fun take(): AnalysisEngine? {
        val e = held
        held = null
        return e
    }
}
