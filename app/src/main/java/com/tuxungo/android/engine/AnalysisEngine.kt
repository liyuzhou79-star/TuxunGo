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

    /**
     * True when the game is beyond rescue — the 「绝」 call. Two gates,
     * both from the user's own definition of the word:
     *  1. The engine's win rate for the leading side is at least
     *     99.4% (his threshold — high enough that the mark stays rare
     *     and meaningful).
     *  2. The trailing side cannot come back even in the best case,
     *     either because the lead exceeds every empty point still on
     *     the board (win them all and you still lose — the "lock"), or
     *     because no large connected unsettled region remains where a
     *     swing could hide (largest undecided region at most
     *     [DECIDED_REGION_MAX] points).
     * The old stage gates (moves played, empty share) are gone: the
     * lock condition already rules the opening out, because an early
     * lead can never exceed the hundreds of empty points left.
     */
    fun isDecided(
        winrateBlack: Double,
        scoreLeadBlack: Double,
        emptyPoints: Int,
        largestUndecidedRegion: Int
    ): Boolean {
        val leaderWinrate = maxOf(winrateBlack, 1.0 - winrateBlack)
        if (leaderWinrate < 0.994) return false
        if (kotlin.math.abs(scoreLeadBlack) > emptyPoints) return true
        return largestUndecidedRegion <= DECIDED_REGION_MAX
    }

    /** Largest undecided empty region that still counts as "no big
     *  open ground left" for the 「绝」 call. */
    const val DECIDED_REGION_MAX = 12

    /**
     * Size of the largest 4-connected region of empty points whose
     * ownership is still undecided (|ownership| below 0.5). A big
     * value means a large unsettled area remains — a capturing race,
     * an invasion target, an unformed moyo — where the game can
     * still swing. [isEmpty] and [ownershipBlack] are both indexed
     * y * width + x.
     */
    fun largestUndecidedRegion(
        ownershipBlack: DoubleArray,
        isEmpty: BooleanArray,
        width: Int,
        height: Int
    ): Int {
        if (width <= 0 || height <= 0) return 0
        val n = width * height
        if (ownershipBlack.size < n || isEmpty.size < n) return 0
        val seen = BooleanArray(n)
        var best = 0
        val stack = ArrayDeque<Int>()
        for (start in 0 until n) {
            if (seen[start] || !isEmpty[start] ||
                kotlin.math.abs(ownershipBlack[start]) >= 0.5
            ) {
                continue
            }
            var size = 0
            seen[start] = true
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                size++
                val cx = cur % width
                val cy = cur / width
                val neighbours = intArrayOf(
                    if (cx > 0) cur - 1 else -1,
                    if (cx < width - 1) cur + 1 else -1,
                    if (cy > 0) cur - width else -1,
                    if (cy < height - 1) cur + width else -1
                )
                for (nb in neighbours) {
                    if (nb < 0 || seen[nb] || !isEmpty[nb] ||
                        kotlin.math.abs(ownershipBlack[nb]) >= 0.5
                    ) {
                        continue
                    }
                    seen[nb] = true
                    stack.addLast(nb)
                }
            }
            if (size > best) best = size
        }
        return best
    }

    /**
     * Share of empty points whose ownership is still undecided
     * (|ownership| below 0.5), 0..1. High values mean the position
     * has unsettled fights — used as a safety gate for deliberate
     * blunders (never blunder into an unsettled position).
     */
    fun unclearShare(
        ownershipBlack: DoubleArray,
        isEmpty: BooleanArray
    ): Double {
        var empty = 0
        var unclear = 0
        val n = minOf(ownershipBlack.size, isEmpty.size)
        for (i in 0 until n) {
            if (!isEmpty[i]) continue
            empty++
            if (kotlin.math.abs(ownershipBlack[i]) < 0.5) unclear++
        }
        return if (empty == 0) 0.0 else unclear.toDouble() / empty
    }

    /**
     * Indices where a win-rate series crosses 50% — the "五五开"
     * turning points of a game. Returns the index AFTER the crossing
     * (the first turn on the new side). A value sitting exactly on
     * 0.5 counts as belonging to neither side, so a series that
     * touches 0.5 and turns back does not register.
     */
    fun winrateCrossings(winrates: List<Double>): List<Int> {
        val out = ArrayList<Int>()
        var prevSign = 0
        for (i in winrates.indices) {
            val sign = when {
                winrates[i] > 0.5 -> 1
                winrates[i] < 0.5 -> -1
                else -> 0
            }
            if (sign != 0) {
                if (prevSign != 0 && sign != prevSign) out.add(i)
                prevSign = sign
            }
        }
        return out
    }

    // ---- Deliberate blunders -------------------------------------
    // Safety rails (all from the user's rules): only from a safely
    // won position, the mistake must be obvious yet never throw the
    // win away, and never while big unsettled ground remains where a
    // dragon could be at stake.

    /** Root position must be at least this good for the mover. */
    const val BLUNDER_MIN_WINRATE = 0.90

    /** Root lead (mover's perspective) required before blundering. */
    const val BLUNDER_MIN_LEAD = 15.0

    /** After the blunder the mover must still be at least this good. */
    const val BLUNDER_FLOOR_WINRATE = 0.80

    /** After the blunder this lead (mover's perspective) must remain. */
    const val BLUNDER_FLOOR_LEAD = 10.0

    /** The blunder must cost at least this much win rate, so it
     *  reads as an obvious mistake, not a quiet inaccuracy. */
    const val BLUNDER_MIN_DROP = 0.05

    /** Never blunder when more than this share of the empty points
     *  is still undecided (unsettled fights / dragons at stake). */
    const val BLUNDER_MAX_UNCLEAR = 0.25

    /**
     * Picks the deliberate blunder for the side to move, or null when
     * no candidate qualifies. Among the qualifying candidates (never
     * the engine's best, never a pass) the one with the LARGEST win
     * rate drop that still respects the floors wins — the most
     * obvious mistake that cannot lose the game. All inputs are from
     * Black's perspective except [sideToMove]; conversion happens
     * inside.
     */
    fun pickBlunder(
        rootWinrateBlack: Double,
        rootScoreLeadBlack: Double,
        candidates: List<Candidate>,
        sideToMove: Stone,
        unclearShare: Double
    ): Candidate? {
        if (unclearShare > BLUNDER_MAX_UNCLEAR) return null
        val sign = if (sideToMove == Stone.BLACK) 1.0 else -1.0
        val rootWr = if (sideToMove == Stone.BLACK) {
            rootWinrateBlack
        } else {
            1.0 - rootWinrateBlack
        }
        val rootLead = sign * rootScoreLeadBlack
        if (rootWr < BLUNDER_MIN_WINRATE || rootLead < BLUNDER_MIN_LEAD) {
            return null
        }
        var best: Candidate? = null
        var bestDrop = 0.0
        for (cand in candidates.drop(1)) {
            if (cand.move == null) continue
            val wr = if (sideToMove == Stone.BLACK) {
                cand.winrateBlack
            } else {
                1.0 - cand.winrateBlack
            }
            val lead = sign * cand.scoreLeadBlack
            val drop = rootWr - wr
            if (drop < BLUNDER_MIN_DROP) continue
            if (wr < BLUNDER_FLOOR_WINRATE || lead < BLUNDER_FLOOR_LEAD) {
                continue
            }
            if (drop > bestDrop) {
                best = cand
                bestDrop = drop
            }
        }
        return best
    }

    // ---- Vulgar moves （俗手） --------------------------------
    // A vulgar move looks completely natural — ideally it is the
    // move a human would pick — but the engine can see it quietly
    // gives something back: a few points of win rate, the kind
    // of move that loses games slowly. Used two ways: the AI can
    // play one on purpose (per-side toggle in the AI panel), and
    // the analysis display marks one among the candidates. The
    // user ruled there are NO position conditions on either use:
    // any side, any score, settled or not. The only gate left is
    // the definition itself — the cost must sit between
    // [VULGAR_MIN_DROP] and [VULGAR_MAX_DROP]: cheaper is just a
    // normal alternative, dearer is a blunder （恶）, not a
    // vulgar move （俗）.

    /** The move must cost at least this much win rate to count as
     *  vulgar at all — cheaper than that is just a normal choice. */
    const val VULGAR_MIN_DROP = 0.02

    /** …but never more than this: a bigger loss is a blunder,
     *  not a vulgar move. */
    const val VULGAR_MAX_DROP = 0.09

    /**
     * Picks the vulgar move among the candidates for the side to
     * move, or null when no candidate qualifies. Qualifying
     * candidates (never the engine's best, never a pass) cost
     * between [VULGAR_MIN_DROP] and [VULGAR_MAX_DROP] win rate.
     * Among them, the human model's own favourite wins when
     * present — nothing looks more natural than the move a human
     * would actually play; otherwise the subtlest (smallest
     * drop) candidate is chosen.
     */
    fun pickVulgar(
        rootWinrateBlack: Double,
        candidates: List<Candidate>,
        humanTopMove: Move?,
        sideToMove: Stone
    ): Candidate? {
        val rootWr = if (sideToMove == Stone.BLACK) {
            rootWinrateBlack
        } else {
            1.0 - rootWinrateBlack
        }
        var subtlest: Candidate? = null
        var subtlestDrop = Double.MAX_VALUE
        var humanPick: Candidate? = null
        for (cand in candidates.drop(1)) {
            val mv = cand.move ?: continue
            val wr = if (sideToMove == Stone.BLACK) {
                cand.winrateBlack
            } else {
                1.0 - cand.winrateBlack
            }
            val drop = rootWr - wr
            if (drop < VULGAR_MIN_DROP || drop > VULGAR_MAX_DROP) continue
            if (humanTopMove != null && mv.x == humanTopMove.x &&
                mv.y == humanTopMove.y
            ) {
                humanPick = cand
            }
            if (drop < subtlestDrop) {
                subtlest = cand
                subtlestDrop = drop
            }
        }
        return humanPick ?: subtlest
    }

    /** Plausible range for the final score, from the engine's score
     *  lead and its standard deviation: lead ± stdev covers the bulk
     *  of the outcomes the engine considers plausible. Null when the
     *  spread is too small to add anything beyond the lead itself.
     *  Pure formatting; unit-tested. */
    fun scoreRangeText(scoreLeadBlack: Double, scoreStdev: Double): String? {
        if (scoreStdev < 0.5) return null
        val lo = scoreLeadBlack - scoreStdev
        val hi = scoreLeadBlack + scoreStdev
        fun fmt(v: Double): String {
            val r = kotlin.math.round(v * 10) / 10
            return if (r == kotlin.math.floor(r)) {
                r.toInt().toString()
            } else {
                r.toString()
            }
        }
        return when {
            lo >= 0 -> "比分范围：大概率黑胜 ${fmt(lo)}～${fmt(hi)} 目"
            hi <= 0 -> "比分范围：大概率白胜 ${fmt(-hi)}～${fmt(-lo)} 目"
            else -> "比分范围：胜负未定，大概率在黑胜 ${fmt(hi)} 目～白胜 ${fmt(-lo)} 目之间"
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
    val ownershipBlack: DoubleArray,
    /** The human-style model's favourite move for this position (null
     *  when it prefers to pass, or when no human model is loaded). */
    val humanTopMove: Move? = null,
    /** GTP vertex of the human favourite ("pass" included), "" when
     *  no human model is loaded. */
    val humanTopVertex: String = "",
    /** How likely a top human player is to play that move, 0..1. */
    val humanTopProb: Double = 0.0,
    /** The human-style model's second favourite board move (null when
     *  it prefers to pass overall, or when no human model is loaded). */
    val humanSecondMove: Move? = null,
    /** GTP vertex of the human second favourite, "" when unavailable. */
    val humanSecondVertex: String = "",
    /** How likely a top human player is to play that move, 0..1. */
    val humanSecondProb: Double = 0.0,
    /** Standard deviation of the engine's final-score estimate: how
     *  wide the plausible range of final scores still is. Large in
     *  the opening / midgame, shrinks towards zero as the game
     *  settles. Sign-invariant (a spread, not a lead). */
    val rootScoreStdev: Double = 0.0
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
        onProvisional: ((AnalysisResult) -> Unit)? = null,
        includeHuman: Boolean = true
    ): AnalysisResult {
        check(isAlive) { "分析引擎未运行。${diagnostics()}" }
        val id = "q${nextId++}"
        currentQueryId = id
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
        if (humanEnabled && includeHuman) {
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
            currentQueryId = null
            return parseResult(resp, size, sideToMove)
        }
    }

    @Volatile
    private var currentQueryId: String? = null

    /**
     * Asks the engine to stop the in-flight query early; analyze()
     * then returns the best result found so far instead of running
     * to its full visit count (protocol support verified locally
     * against v1.15.3: a final response for the query id arrives
     * promptly after the terminate action). Used when the board has
     * already moved on and the running result would be discarded
     * anyway. Safe to call when nothing is running.
     */
    fun interrupt() {
        val id = currentQueryId ?: return
        try {
            synchronized(input) {
                val msg = JSONObject()
                msg.put("id", id)
                msg.put("action", "terminate")
                input.write(msg.toString())
                input.newLine()
                input.flush()
            }
        } catch (_: Exception) {
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
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        includeOwnership: Boolean = false,
        onOwnership: (Int, DoubleArray) -> Unit = { _, _ -> }
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
        query.put("includeOwnership", includeOwnership)
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
            if (includeOwnership) {
                val ownArr = resp.optJSONArray("ownership")
                if (ownArr != null && ownArr.length() == size.area) {
                    val raw = DoubleArray(ownArr.length()) {
                        ownArr.optDouble(it, 0.0)
                    }
                    // Same perspective rule as the root numbers:
                    // ownership is reported for the player to move
                    // at this turn.
                    onOwnership(turn, AnalysisMath.ownershipToBlack(raw, mover))
                }
            }
            onProgress(byTurn.size, total)
        }
        return wantedTurns.map { byTurn[it] ?: TurnStat(it, 0.5, 0.0, null, false) }
    }

    private fun parseResult(resp: JSONObject, size: BoardSize, sideToMove: Stone): AnalysisResult {
        val root = resp.optJSONObject("rootInfo")
        val rootWr = root?.optDouble("winrate", 0.5) ?: 0.5
        val rootLead = root?.optDouble("scoreLead", 0.0) ?: 0.0
        val rootStdev = root?.optDouble("scoreStdev", 0.0) ?: 0.0
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

        // Human-style favourites: the two highest board points of the
        // human policy array (row-major order from the top, pass last).
        var humanTopMove: Move? = null
        var humanTopVertex = ""
        var humanTopProb = 0.0
        var humanSecondMove: Move? = null
        var humanSecondVertex = ""
        var humanSecondProb = 0.0
        val humanArr = resp.optJSONArray("humanPolicy")
        if (humanArr != null && humanArr.length() >= size.area) {
            var bestIdx = -1
            var bestVal = 0.0
            var secondIdx = -1
            var secondVal = 0.0
            for (i in 0 until size.area) {
                val v = humanArr.optDouble(i, 0.0)
                if (v > bestVal) {
                    secondIdx = bestIdx
                    secondVal = bestVal
                    bestVal = v
                    bestIdx = i
                } else if (v > secondVal) {
                    secondVal = v
                    secondIdx = i
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
                if (secondIdx >= 0 && secondVal > 0.001) {
                    val second = Move(
                        sideToMove,
                        secondIdx % size.width,
                        secondIdx / size.width
                    )
                    humanSecondMove = second
                    humanSecondVertex = KataEngine.toVertex(second, size)
                    humanSecondProb = secondVal
                }
            } else if (passProb > 0.001) {
                humanTopVertex = "pass"
                humanTopProb = passProb
            }
        }
        return AnalysisResult(
            sideToMove, rootWrB, rootLeadB, candidates, ownership,
            humanTopMove, humanTopVertex, humanTopProb,
            humanSecondMove, humanSecondVertex, humanSecondProb,
            rootScoreStdev = rootStdev
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
                nnMaxBatchSize = 32
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
