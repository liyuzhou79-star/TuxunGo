package com.tuxungo.android.engine

import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone

/** Win rate / score lead for one turn of a game, from Black's view. */
data class TurnStat(
    val turn: Int,
    val winrateBlack: Double,
    val scoreLeadBlack: Double,
    val bestMove: Move?,
    val bestIsPass: Boolean,
    /**
     * Win-rate gap between the engine's top choice and its second
     * choice at this turn (0..1): how much the best move stood out.
     * 0 when unknown (stats recorded before this was tracked).
     */
    val bestGap: Double = 0.0,
    /**
     * The top choice's principal variation as space-joined GTP
     * vertices ("Q16 R17 pass D4"), for showing the AI's suggested
     * continuation as ghost stones in review. "" when unknown
     * (stats recorded before this was tracked).
     */
    val bestPv: String = "",
    /**
     * The move that PRODUCED this position was a deliberate blunder:
     * the full-strength AI, safely winning, chose a clearly weaker
     * move on purpose (the user's 「故意恶手」 feature, off by
     * default). Marked so review can show it as a deliberate gift
     * instead of an ordinary blunder. False for stats recorded
     * before this was tracked.
     */
    val deliberate: Boolean = false,
    /**
     * The move that produced this position earned a decisive badge:
     * 0 = none, 1 = global 「绝」, 2 = regional 「定」, 3 = ko
     * resolved 「销」 (see [DecisiveMath]). 0 for stats recorded
     * before this was tracked.
     */
    val decisive: Int = 0,
    /** [DecisiveReason] code for [decisive]; 0 when none. */
    val decisiveReason: Int = 0,
    /**
     * The move that produced this position was a deliberate vulgar
     * move (「俗」手): the full-strength AI, safely winning, chose
     * a natural-looking but subtly inferior move on purpose (per-side
     * opt-in). False for stats recorded before this was tracked.
     */
    val vulgar: Boolean = false
)

enum class MarkKind { BLUNDER, BRILLIANT, KEY, DELIBERATE, VULGAR }

data class MoveMark(
    val moveNumber: Int, // 1-based
    val kind: MarkKind,
    /** Win-rate change for the player who moved, in percentage points. */
    val deltaPercent: Double
)

/** Move-quality math for the review screen, plus stat (de)serializing. */
object ReviewMath {

    const val BLUNDER_DROP = 15.0

    /**
     * A top-choice move only counts as brilliant when it clearly
     * stood out from the alternatives (top-vs-second win-rate gap).
     * Without this, every quiet position floods the list with
     * "brilliant" moves that were simply the obvious play.
     */
    const val BRILLIANT_GAP = 0.05

    fun winrateFor(stat: TurnStat, side: Stone): Double =
        if (side == Stone.BLACK) stat.winrateBlack else 1.0 - stat.winrateBlack

    /**
     * Classifies every move. A move is a blunder when the mover's win
     * rate drops by [BLUNDER_DROP]+ points; brilliant when it matches
     * the engine's top choice without giving anything back AND that
     * choice clearly stood out from the alternatives ([BRILLIANT_GAP]);
     * the key move is the single biggest swing of the game.
     */
    fun marks(moves: List<Move>, turns: List<TurnStat>): List<MoveMark> {
        // turns must be the DENSE per-turn list (index == turn, one
        // entry per move plus the initial position). A sparse list
        // (e.g. only the turns a live game happened to analyze) used
        // to crash the caller with an index error — refuse instead.
        if (turns.size != moves.size + 1 || moves.isEmpty()) return emptyList()
        val out = mutableListOf<MoveMark>()
        var keyMove = -1
        var keyAbs = -1.0
        for (i in moves.indices) {
            val mover = moves[i].stone
            val before = winrateFor(turns[i], mover)
            val after = winrateFor(turns[i + 1], mover)
            val delta = (after - before) * 100.0
            val abs = kotlin.math.abs(delta)
            if (abs > keyAbs) {
                keyAbs = abs
                keyMove = i + 1
            }
            val best = turns[i].bestMove
            val played = moves[i]
            val matchesTop = if (played.isPass) {
                turns[i].bestIsPass
            } else {
                best != null && best.x == played.x && best.y == played.y
            }
            when {
                // A deliberate blunder is marked as itself, never as
                // an ordinary blunder: the AI meant to give that back.
                turns[i + 1].deliberate ->
                    out.add(MoveMark(i + 1, MarkKind.DELIBERATE, delta))
                // A deliberate vulgar move likewise: it looks like a
                // natural move, the AI chose it on purpose.
                turns[i + 1].vulgar ->
                    out.add(MoveMark(i + 1, MarkKind.VULGAR, delta))
                delta <= -BLUNDER_DROP -> out.add(MoveMark(i + 1, MarkKind.BLUNDER, delta))
                matchesTop && delta >= -2.0 && turns[i].bestGap >= BRILLIANT_GAP ->
                    out.add(MoveMark(i + 1, MarkKind.BRILLIANT, delta))
            }
        }
        if (keyMove > 0) {
            // The key move keeps any blunder/brilliant mark it earned and
            // gains the KEY mark on top; the UI shows both badges.
            val existing = out.firstOrNull { it.moveNumber == keyMove }
            val delta = existing?.deltaPercent ?: run {
                val mover = moves[keyMove - 1].stone
                (winrateFor(turns[keyMove], mover) - winrateFor(turns[keyMove - 1], mover)) * 100.0
            }
            out.add(MoveMark(keyMove, MarkKind.KEY, delta))
        }
        return out.sortedBy { it.moveNumber }
    }

    /** One blunder entry for the whole-game summary card. */
    data class BlunderLine(
        val moveNumber: Int,
        val stone: Stone,
        /** Win rate the mover gave away, in percent points (> 0). */
        val dropPercent: Double,
        /** The engine's top choice as a GTP vertex, "" when unknown. */
        val bestText: String
    )

    /** Whole-game summary shown as the review commentary card. */
    data class GameSummary(
        /** Move number of the largest win-rate swing (0 = none). */
        val turningPoint: Int,
        /** Black win rate (0..1) just before / after that move. */
        val turningBeforeBlack: Double,
        val turningAfterBlack: Double,
        /** Up to three worst blunders, worst first. */
        val blunders: List<BlunderLine>,
        val blackBlunders: Int,
        val whiteBlunders: Int,
        val brilliantCount: Int,
        /** Move numbers the AI played as deliberate blunders. */
        val deliberateMoves: List<Int> = emptyList(),
        /** Move numbers the AI played as deliberate vulgar moves. */
        val vulgarMoves: List<Int> = emptyList()
    )

    /**
     * Builds the commentary-card summary from the same marks the
     * chart shows — no extra engine work, just aggregation.
     */
    fun gameSummary(
        moves: List<Move>,
        turns: List<TurnStat>,
        size: BoardSize
    ): GameSummary? {
        if (turns.size != moves.size + 1 || moves.isEmpty()) return null
        val allMarks = marks(moves, turns)
        val key = allMarks.firstOrNull { it.kind == MarkKind.KEY }
        val blunderMarks = allMarks.filter { it.kind == MarkKind.BLUNDER }
        val lines = blunderMarks
            .sortedBy { it.deltaPercent }
            .take(3)
            .map { mk ->
                val stat = turns[mk.moveNumber - 1]
                val bestText = when {
                    stat.bestIsPass -> "停一手"
                    stat.bestMove != null ->
                        KataEngine.toVertex(stat.bestMove!!, size)
                    else -> ""
                }
                BlunderLine(
                    moveNumber = mk.moveNumber,
                    stone = moves[mk.moveNumber - 1].stone,
                    dropPercent = -mk.deltaPercent,
                    bestText = bestText
                )
            }
        return GameSummary(
            turningPoint = key?.moveNumber ?: 0,
            turningBeforeBlack = if (key != null) {
                turns[key.moveNumber - 1].winrateBlack
            } else {
                0.0
            },
            turningAfterBlack = if (key != null) {
                turns[key.moveNumber].winrateBlack
            } else {
                0.0
            },
            blunders = lines,
            blackBlunders = blunderMarks.count {
                moves[it.moveNumber - 1].stone == Stone.BLACK
            },
            whiteBlunders = blunderMarks.count {
                moves[it.moveNumber - 1].stone == Stone.WHITE
            },
            brilliantCount = allMarks.count { it.kind == MarkKind.BRILLIANT },
            deliberateMoves = allMarks
                .filter { it.kind == MarkKind.DELIBERATE }
                .map { it.moveNumber },
            vulgarMoves = allMarks
                .filter { it.kind == MarkKind.VULGAR }
                .map { it.moveNumber }
        )
    }

    /**
     * The first turn (== move number) whose position the engine
     * already considered beyond rescue, using the score-lock half of
     * the 「绝」 rule only: win rate at least 99.4% and a lead larger
     * than every empty point then on the board. (The region half
     * needs ownership arrays, which recorded stats do not keep.)
     * [emptyByTurn] is indexed by turn. Null when the game was never
     * locked. Used to pin the persistent 「绝」 badge on the sealing
     * stone in review.
     */
    fun firstDecidedTurn(turns: List<TurnStat>, emptyByTurn: IntArray): Int? {
        for (t in 1 until turns.size) {
            val s = turns[t]
            val empty = if (t < emptyByTurn.size) {
                emptyByTurn[t]
            } else {
                Int.MAX_VALUE
            }
            if (AnalysisMath.isDecided(
                    s.winrateBlack, s.scoreLeadBlack,
                    empty, Int.MAX_VALUE
                )
            ) {
                return t
            }
        }
        return null
    }

    /**
     * One stat's body: "wr,lead,bestX,bestY,gap,pv" plus optional
     * trailing fields, in order: deliberate(1), decisive kind,
     * decisive reason code, vulgar(1). Trailing fields are written
     * only up to the last one that is set (earlier gaps are padded
     * with 0), so old files stay short and old readers — which only
     * look at the fields they know — are unaffected.
     */
    private fun encodeBody(t: TurnStat): String {
        val bx = t.bestMove?.x ?: -1
        val by = t.bestMove?.y ?: -1
        val base = "${fmt(t.winrateBlack)},${fmt(t.scoreLeadBlack)}," +
            "$bx,$by,${fmt(t.bestGap)},${t.bestPv}"
        val tail = listOf(
            if (t.deliberate) "1" else "0",
            t.decisive.toString(),
            t.decisiveReason.toString(),
            if (t.vulgar) "1" else "0"
        )
        val flags = listOf(
            t.deliberate, t.decisive != 0, t.decisiveReason != 0, t.vulgar
        )
        val last = flags.indexOfLast { it }
        if (last < 0) return base
        return base + tail.take(last + 1).joinToString(",", prefix = ",")
    }

    private fun statFromFields(turn: Int, f: List<String>): TurnStat {
        val bx = f.getOrNull(2)?.toIntOrNull() ?: -1
        val by = f.getOrNull(3)?.toIntOrNull() ?: -1
        return TurnStat(
            turn = turn,
            winrateBlack = f.getOrNull(0)?.toDoubleOrNull() ?: 0.5,
            scoreLeadBlack = f.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            // bestMove's stone is not stored; marks() only needs its
            // coordinates, so park them on a throwaway Move.
            bestMove = if (bx >= 0 && by >= 0) Move(Stone.BLACK, bx, by) else null,
            bestIsPass = bx < 0,
            bestGap = f.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
            bestPv = f.getOrNull(5) ?: "",
            deliberate = f.getOrNull(6) == "1",
            decisive = f.getOrNull(7)?.toIntOrNull() ?: 0,
            decisiveReason = f.getOrNull(8)?.toIntOrNull() ?: 0,
            vulgar = f.getOrNull(9) == "1"
        )
    }

    /** "wr,lead,bestX,bestY,gap;..." — best is -1,-1 for pass / unknown. */
    fun encodeTurns(turns: List<TurnStat>): String =
        turns.joinToString(";") { encodeBody(it) }

    fun decodeTurns(text: String): List<TurnStat> {
        if (text.isBlank()) return emptyList()
        return text.split(";").mapIndexed { idx, part ->
            statFromFields(idx, part.split(","))
        }
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.3f", v)

    /**
     * Sparse form: "turn=wr,lead,bx,by;..." — only the turns present in
     * the map. Used for stats recorded live during a game, where some
     * turns may be missing and get filled in at review time.
     */
    fun encodeSparse(stats: Map<Int, TurnStat>): String =
        stats.entries.sortedBy { it.key }.joinToString(";") { (turn, t) ->
            "$turn=${encodeBody(t)}"
        }

    /** Parses the sparse form; also tolerates the dense form. */
    fun decodeSparse(text: String?): Map<Int, TurnStat> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = HashMap<Int, TurnStat>()
        val parts = text.split(";")
        for ((idx, part) in parts.withIndex()) {
            if (part.isBlank()) continue
            val eq = part.indexOf('=')
            val turn: Int
            val body: String
            if (eq >= 0) {
                turn = part.substring(0, eq).toIntOrNull() ?: continue
                body = part.substring(eq + 1)
            } else {
                turn = idx
                body = part
            }
            out[turn] = statFromFields(turn, body.split(","))
        }
        return out
    }

    /**
     * Parse a stored PV string ("Q16 R17 pass D4") into ghost
     * moves, alternating colours starting with [first]. Turns
     * alternate even through passes and unparsable tokens.
     */
    fun parsePv(pv: String, size: BoardSize, first: Stone): List<Move> {
        if (pv.isBlank()) return emptyList()
        val out = ArrayList<Move>()
        var stone = first
        for (token in pv.trim().split(Regex("\\s+"))) {
            val mv = if (token.equals("pass", ignoreCase = true)) {
                Move.pass(stone)
            } else {
                KataEngine.fromVertex(token, stone, size)
            }
            if (mv != null) out.add(mv)
            stone = stone.other()
        }
        return out
    }
}

/** Which family a decisive badge belongs to. */
enum class DecisiveKind { GLOBAL, REGIONAL, KO }

/** Why a move earned its decisive badge — the review shows this. */
enum class DecisiveReason(val code: Int, val label: String, val detail: String) {
    LOCK(1, "全局锁定",
        "胜率已经长时间稳在 94% 以上，地盘基本分完：落后方把剩下的空全占掉也超不过，" +
            "优势方的地盘里也没有能打进去的地方。这一手把全盘锁死。"),
    ENDGAME(2, "官子收尽",
        "这一手下完，全场官子收尽、地盘全部分完，胜负再无变数。"),
    COMEBACK(3, "逆转定局",
        "这一方一度大落后，这一手把逆转坐实：翻上来之后稳住十几手没出大破绽，" +
            "胜率不再大起大落。"),
    DRAGON_KILL(4, "大龙定死",
        "这一手掐断了对方大龙最后一条活路，大龙整条归了自己，对方再也翻不回来。"),
    DRAGON_SAVE(5, "大龙补活",
        "这一手把自己大龙最后的弱点补上，大龙活定，不再受威胁。"),
    REGION_LIVE(6, "区域活出",
        "这一手把一大块棋活定，这片区域的战斗到此结束。"),
    EXPANSION(7, "扩展定型",
        "这一手把新扩展出去的地盘走厚、分配完毕，这片区域就此定型。"),
    REGION_SETTLE(8, "领地定型",
        "这一手把这片区域的归属定死，这片战斗到此结束。"),
    KO_RESOLVED(9, "销劫",
        "这一手把劫消了：劫争到此结束，这个劫不再是威胁。");

    companion object {
        fun fromCode(code: Int): DecisiveReason? =
            values().firstOrNull { it.code == code }
    }
}

/** One decisive badge earned by one move. */
data class DecisiveMark(
    val moveNumber: Int, // 1-based
    val kind: DecisiveKind,
    val reason: DecisiveReason
)

/**
 * Detects the decisive moves of a game — the user's 「绝 / 定 /
 * 销」 badges. A game may earn several: 「绝」 when a move seals
 * the whole game, 「定」 when a move settles one region or dragon
 * for good, 「销」 when a move resolves a ko. Every detector is
 * conservative: ownership-based calls must PERSIST for two more
 * turns before they count (a one-ply mirage earns nothing), and
 * the global lock uses a pessimistic bound — the trailer is
 * credited with every still-unallocated empty point and must
 * still lose.
 *
 * Inputs are deliberately plain: per-turn stats and ownership
 * (both sparse maps — missing turns just disable the detectors
 * that need them), plus the board at every turn (index == turn),
 * which the caller replays once. Pure and unit-tested.
 */
object DecisiveMath {

    // Global lock: calm, high win rate for a whole window.
    const val LOCK_WINRATE = 0.94
    const val LOCK_WINDOW = 6
    const val LOCK_MAX_SWING = 0.05
    const val LOCK_CONFIRM_WINRATE = 0.90

    // Comeback: once at most this (leader's win rate), then at
    // least COMEBACK_HIGH, with a long clean, calm stretch.
    const val COMEBACK_DEEP = 0.32
    const val COMEBACK_HIGH = 0.90
    const val COMEBACK_CLEAN_TURNS = 15
    const val COMEBACK_CALM_TURNS = 10
    const val COMEBACK_MAX_DROP = 0.08
    const val COMEBACK_MAX_SWING = 0.06

    /** Ownership mean at which a group / region counts as settled. */
    const val FIRM_GROUP = 0.55
    const val FIRM_REGION = 0.60

    fun kindCode(kind: DecisiveKind): Int = when (kind) {
        DecisiveKind.GLOBAL -> 1
        DecisiveKind.REGIONAL -> 2
        DecisiveKind.KO -> 3
    }

    fun kindFromCode(code: Int): DecisiveKind? = when (code) {
        1 -> DecisiveKind.GLOBAL
        2 -> DecisiveKind.REGIONAL
        3 -> DecisiveKind.KO
        else -> null
    }

    /** Marks stored inside a dense per-turn stat list. */
    fun marksFromStats(turns: List<TurnStat>): List<DecisiveMark> {
        val out = ArrayList<DecisiveMark>()
        for (t in 1 until turns.size) {
            val s = turns[t]
            val kind = kindFromCode(s.decisive) ?: continue
            val reason = DecisiveReason.fromCode(s.decisiveReason) ?: continue
            out.add(DecisiveMark(t, kind, reason))
        }
        return out
    }

    /**
     * The (decisive, decisiveReason) stat fields for one turn: the
     * highest-priority mark on that move (global > regional > ko —
     * ko is re-derivable from the boards alone, so it yields).
     */
    fun statFieldsFor(marks: List<DecisiveMark>, turn: Int): Pair<Int, Int> {
        val at = marks.filter { it.moveNumber == turn }
        val pick = at.firstOrNull { it.kind == DecisiveKind.GLOBAL }
            ?: at.firstOrNull { it.kind == DecisiveKind.REGIONAL }
            ?: at.firstOrNull()
            ?: return 0 to 0
        return kindCode(pick.kind) to pick.reason.code
    }

    fun detect(
        size: BoardSize,
        moves: List<Move>,
        stats: Map<Int, TurnStat>,
        ownership: Map<Int, DoubleArray>,
        boards: List<Array<Stone?>>,
        finalized: Boolean
    ): List<DecisiveMark> {
        val lastTurn = moves.size
        if (lastTurn == 0 || boards.size < lastTurn + 1) return emptyList()
        val out = ArrayList<DecisiveMark>()
        out += koMarks(size, moves, boards)
        val end = endgameMark(size, moves, stats, ownership, boards)
        val lock = lockMark(size, moves, stats, ownership, boards, finalized)
        val comeback = comebackMark(moves, stats)
        val globals = listOfNotNull(lock, end?.first, comeback)
        val kept = ArrayList<DecisiveMark>()
        for (g in globals.sortedBy { it.moveNumber }) {
            if (kept.any { it.moveNumber == g.moveNumber }) continue
            kept.add(g)
        }
        out += kept
        out += regionalMarks(
            size, moves, ownership, boards, finalized, end?.second
        )
        return out.sortedBy { it.moveNumber }
    }

    // ---- shared helpers ------------------------------------------

    private fun wrFor(stat: TurnStat, stone: Stone): Double =
        if (stone == Stone.BLACK) stat.winrateBlack else 1.0 - stat.winrateBlack

    private fun leadFor(stat: TurnStat, stone: Stone): Double =
        if (stone == Stone.BLACK) stat.scoreLeadBlack else -stat.scoreLeadBlack

    private fun leaderOf(stat: TurnStat): Stone =
        if (stat.winrateBlack >= 0.5) Stone.BLACK else Stone.WHITE

    /**
     * Empty points whose ownership is still unallocated
     * (|own| < 0.5). Without ownership this falls back to ALL
     * empty points — the stricter bound, so a stats-only lock
     * can never be a false one.
     */
    private fun unallocated(board: Array<Stone?>, own: DoubleArray?): Int {
        var c = 0
        for (i in board.indices) {
            if (board[i] != null) continue
            if (own == null || kotlin.math.abs(own[i]) < 0.5) c++
        }
        return c
    }

    fun neighbors(idx: Int, size: BoardSize): List<Int> {
        val x = idx % size.width
        val y = idx / size.width
        val out = ArrayList<Int>(4)
        if (x > 0) out.add(idx - 1)
        if (x < size.width - 1) out.add(idx + 1)
        if (y > 0) out.add(idx - size.width)
        if (y < size.height - 1) out.add(idx + size.width)
        return out
    }

    private data class GroupInfo(
        val points: List<Int>,
        val color: Stone,
        val liberties: Set<Int>
    )

    private fun computeGroups(
        board: Array<Stone?>,
        size: BoardSize
    ): List<GroupInfo> {
        val seen = BooleanArray(size.area)
        val out = ArrayList<GroupInfo>()
        for (start in 0 until size.area) {
            val color = board[start] ?: continue
            if (seen[start]) continue
            val points = ArrayList<Int>()
            val libs = HashSet<Int>()
            val stack = ArrayDeque<Int>()
            seen[start] = true
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                points.add(cur)
                for (nb in neighbors(cur, size)) {
                    val s = board[nb]
                    when {
                        s == null -> libs.add(nb)
                        s == color && !seen[nb] -> {
                            seen[nb] = true
                            stack.addLast(nb)
                        }
                    }
                }
            }
            out.add(GroupInfo(points, color, libs))
        }
        return out
    }

    private fun emptyRegions(
        board: Array<Stone?>,
        size: BoardSize
    ): List<List<Int>> {
        val seen = BooleanArray(size.area)
        val out = ArrayList<List<Int>>()
        for (start in 0 until size.area) {
            if (board[start] != null || seen[start]) continue
            val points = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            seen[start] = true
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                points.add(cur)
                for (nb in neighbors(cur, size)) {
                    if (board[nb] == null && !seen[nb]) {
                        seen[nb] = true
                        stack.addLast(nb)
                    }
                }
            }
            out.add(points)
        }
        return out
    }

    // ---- global lock ----------------------------------------------

    private fun lockMark(
        size: BoardSize,
        moves: List<Move>,
        stats: Map<Int, TurnStat>,
        ownership: Map<Int, DoubleArray>,
        boards: List<Array<Stone?>>,
        finalized: Boolean
    ): DecisiveMark? {
        val lastTurn = moves.size
        for (t in 1..lastTurn) {
            val st = stats[t] ?: continue
            val leader = leaderOf(st)
            if (wrFor(st, leader) < LOCK_WINRATE) continue
            var ok = true
            var lo = Double.MAX_VALUE
            var hi = 0.0
            for (s in (t - LOCK_WINDOW + 1)..t) {
                val ss = stats[s]
                if (ss == null) {
                    ok = false
                    break
                }
                val w = wrFor(ss, leader)
                if (w < LOCK_WINRATE) {
                    ok = false
                    break
                }
                lo = minOf(lo, w)
                hi = maxOf(hi, w)
            }
            if (!ok || hi - lo > LOCK_MAX_SWING) continue
            if (kotlin.math.abs(st.scoreLeadBlack) <=
                unallocated(boards[t], ownership[t])
            ) {
                continue
            }
            val own = ownership[t]
            if (own != null && hasHole(own, boards[t], size, leader)) continue
            var m = t
            while (m >= 1 && moves[m - 1].stone != leader) m--
            if (m < 1 || t - m > 3) continue
            if (!lockConfirmed(
                    t, leader, stats, ownership, boards, lastTurn, finalized
                )
            ) {
                continue
            }
            return DecisiveMark(m, DecisiveKind.GLOBAL, DecisiveReason.LOCK)
        }
        return null
    }

    private fun lockConfirmed(
        t: Int,
        leader: Stone,
        stats: Map<Int, TurnStat>,
        ownership: Map<Int, DoubleArray>,
        boards: List<Array<Stone?>>,
        lastTurn: Int,
        finalized: Boolean
    ): Boolean {
        for (k in 1..2) {
            val tk = t + k
            if (tk > lastTurn) return finalized
            val ss = stats[tk] ?: return finalized
            if (wrFor(ss, leader) < LOCK_CONFIRM_WINRATE) return false
            if (kotlin.math.abs(ss.scoreLeadBlack) <=
                unallocated(boards[tk], ownership[tk])
            ) {
                return false
            }
        }
        return true
    }

    /**
     * A hole in the leader's firm ground: an empty point the
     * leader does not firmly own, completely surrounded by points
     * the leader firmly owns — an invasion corridor the "no
     * breakthrough" condition forbids.
     */
    private fun hasHole(
        own: DoubleArray,
        board: Array<Stone?>,
        size: BoardSize,
        leader: Stone
    ): Boolean {
        val sign = if (leader == Stone.BLACK) 1.0 else -1.0
        for (p in board.indices) {
            if (board[p] != null) continue
            if (own[p] * sign >= 0.5) continue
            var surrounded = true
            for (nb in neighbors(p, size)) {
                if (own[nb] * sign < 0.8) {
                    surrounded = false
                    break
                }
            }
            if (surrounded) return true
        }
        return false
    }

    // ---- endgame complete ------------------------------------------

    private fun endgameMark(
        size: BoardSize,
        moves: List<Move>,
        stats: Map<Int, TurnStat>,
        ownership: Map<Int, DoubleArray>,
        boards: List<Array<Stone?>>
    ): Pair<DecisiveMark, Int>? {
        val lastTurn = moves.size
        for (t in 1..lastTurn) {
            val own = ownership[t] ?: continue
            if (unallocated(boards[t], own) != 0) continue
            var stays = true
            for (k in (t + 1)..lastTurn) {
                val o2 = ownership[k] ?: continue
                if (unallocated(boards[k], o2) != 0) {
                    stays = false
                    break
                }
            }
            if (!stays) continue
            val st = stats[t] ?: return null
            if (st.scoreLeadBlack == 0.0) return null
            val leader = if (st.scoreLeadBlack > 0) {
                Stone.BLACK
            } else {
                Stone.WHITE
            }
            var m = t
            while (m >= 1 && moves[m - 1].stone != leader) m--
            if (m < 1) return null
            return DecisiveMark(
                m, DecisiveKind.GLOBAL, DecisiveReason.ENDGAME
            ) to t
        }
        return null
    }

    // ---- comeback ---------------------------------------------------

    private fun comebackMark(
        moves: List<Move>,
        stats: Map<Int, TurnStat>
    ): DecisiveMark? {
        val lastTurn = moves.size
        val endStat = stats[lastTurn] ?: return null
        val leader = leaderOf(endStat)
        if (wrFor(endStat, leader) < 0.85) return null
        for (t in 1..lastTurn) {
            if (t < COMEBACK_CLEAN_TURNS) continue
            val st = stats[t] ?: continue
            if (wrFor(st, leader) < COMEBACK_HIGH) continue
            val mv = moves[t - 1]
            if (mv.stone != leader || mv.isPass) continue
            var deep = false
            for (s in 0..(t - COMEBACK_CALM_TURNS)) {
                val ss = stats[s] ?: continue
                if (wrFor(ss, leader) <= COMEBACK_DEEP) {
                    deep = true
                    break
                }
            }
            if (!deep) continue
            var ok = true
            for (s in (t - COMEBACK_CLEAN_TURNS) until t) {
                val before = stats[s]
                val after = stats[s + 1]
                if (before == null || after == null) {
                    ok = false
                    break
                }
                val mover = moves.getOrNull(s) ?: continue
                if (mover.stone == leader &&
                    wrFor(before, leader) - wrFor(after, leader) >=
                    COMEBACK_MAX_DROP
                ) {
                    ok = false
                    break
                }
            }
            if (!ok) continue
            var lo = Double.MAX_VALUE
            var hi = 0.0
            for (s in (t - COMEBACK_CALM_TURNS + 1)..t) {
                val ss = stats[s]
                if (ss == null) {
                    ok = false
                    break
                }
                val w = wrFor(ss, leader)
                lo = minOf(lo, w)
                hi = maxOf(hi, w)
            }
            if (!ok || hi - lo > COMEBACK_MAX_SWING) continue
            val prev = stats[t - 1] ?: continue
            val gain = wrFor(st, leader) - wrFor(prev, leader)
            val leadGain = leadFor(st, leader) - leadFor(prev, leader)
            if (gain < 0.02 && leadGain < 2.0) continue
            return DecisiveMark(t, DecisiveKind.GLOBAL, DecisiveReason.COMEBACK)
        }
        return null
    }

    // ---- regional settles -------------------------------------------

    private fun vitality(
        own: DoubleArray,
        points: List<Int>,
        stone: Stone
    ): Double {
        if (points.isEmpty()) return 0.0
        val sign = if (stone == Stone.BLACK) 1.0 else -1.0
        var sum = 0.0
        for (p in points) sum += own[p]
        return sum / points.size * sign
    }

    private fun meanOwn(own: DoubleArray, points: List<Int>): Double {
        if (points.isEmpty()) return 0.0
        var sum = 0.0
        for (p in points) sum += own[p]
        return sum / points.size
    }

    /**
     * True when the settled state of [points] still holds two
     * turns later. [settledDir] is +1 when the points settled in
     * [stone]'s favour (a group alive, a region owned by [stone]),
     * -1 when they settled against it (a group dead). Missing
     * later data only passes once the record is [finalized].
     */
    private fun persists(
        points: List<Int>,
        stone: Stone,
        settledDir: Int,
        t: Int,
        ownership: Map<Int, DoubleArray>,
        lastTurn: Int,
        finalized: Boolean,
        group: Boolean
    ): Boolean {
        for (k in 1..2) {
            val tk = t + k
            if (tk > lastTurn) return finalized
            val o = ownership[tk] ?: return finalized
            val v = if (group) {
                vitality(o, points, stone) * settledDir
            } else {
                meanOwn(o, points) *
                    (if (stone == Stone.BLACK) 1.0 else -1.0) * settledDir
            }
            if (v < 0.45) return false
        }
        return true
    }

    private fun regionalMarks(
        size: BoardSize,
        moves: List<Move>,
        ownership: Map<Int, DoubleArray>,
        boards: List<Array<Stone?>>,
        finalized: Boolean,
        endTurn: Int?
    ): List<DecisiveMark> {
        val lastTurn = moves.size
        val area = size.area
        // The user's 32-stone bar for a "big block", scaled to the
        // board (32 on 19x19).
        val groupMin = maxOf(8, (32.0 * area / 361).toInt())
        val regionMin = maxOf(10, (18.0 * area / 361).toInt())
        val out = ArrayList<DecisiveMark>()
        val markedSets = ArrayList<Set<Int>>()
        fun alreadyMarked(points: Set<Int>): Boolean =
            markedSets.any { prev ->
                val inter = points.count { it in prev }
                inter * 2 >= minOf(points.size, prev.size)
            }
        for (t in 1..lastTurn) {
            if (endTurn != null && t > endTurn) break
            val ownPrev = ownership[t - 1] ?: continue
            val ownNow = ownership[t] ?: continue
            val mv = moves[t - 1]
            if (mv.isPass) continue
            val boardNow = boards[t]
            for (g in computeGroups(boardNow, size)) {
                if (g.points.size < groupMin) continue
                val vitNow = vitality(ownNow, g.points, g.color)
                if (kotlin.math.abs(vitNow) < FIRM_GROUP) continue
                val vitPrev = vitality(ownPrev, g.points, g.color)
                if (kotlin.math.abs(vitPrev) >= FIRM_GROUP) continue
                val beneficiary = if (vitNow > 0) {
                    g.color
                } else {
                    g.color.other()
                }
                if (mv.stone != beneficiary) continue
                if (!persists(
                        g.points, g.color, if (vitNow > 0) 1 else -1,
                        t, ownership, lastTurn, finalized, group = true
                    )
                ) {
                    continue
                }
                val gset = g.points.toSet()
                if (alreadyMarked(gset)) continue
                markedSets.add(gset)
                val reason = when {
                    vitNow < 0 -> DecisiveReason.DRAGON_KILL
                    vitPrev <= 0.05 -> DecisiveReason.DRAGON_SAVE
                    else -> DecisiveReason.REGION_LIVE
                }
                out.add(DecisiveMark(t, DecisiveKind.REGIONAL, reason))
            }
            for (region in emptyRegions(boardNow, size)) {
                if (region.size < regionMin) continue
                val meanNow = meanOwn(ownNow, region)
                if (kotlin.math.abs(meanNow) < FIRM_REGION) continue
                val meanPrev = meanOwn(ownPrev, region)
                if (kotlin.math.abs(meanPrev) >= FIRM_REGION) continue
                val beneficiary = if (meanNow > 0) {
                    Stone.BLACK
                } else {
                    Stone.WHITE
                }
                if (mv.stone != beneficiary) continue
                if (!persists(
                        region, beneficiary, 1,
                        t, ownership, lastTurn, finalized, group = false
                    )
                ) {
                    continue
                }
                val rset = region.toSet()
                if (alreadyMarked(rset)) continue
                markedSets.add(rset)
                val moveIdx = mv.y * size.width + mv.x
                val adjacent = region.any { p ->
                    neighbors(p, size).any { it == moveIdx }
                }
                out.add(
                    DecisiveMark(
                        t, DecisiveKind.REGIONAL,
                        if (adjacent) {
                            DecisiveReason.EXPANSION
                        } else {
                            DecisiveReason.REGION_SETTLE
                        }
                    )
                )
            }
        }
        return out
    }

    // ---- ko resolution ------------------------------------------------

    /**
     * Ko shapes on one board: empty point -> colour of the ko
     * stone. A ko shape is an empty point whose neighbours are all
     * stones of one colour, exactly one of them a single stone
     * whose only liberty is that point — the mirrored recapture
     * lands in atari again, which is what makes it a ko and not
     * just an atari.
     */
    fun koPoints(
        board: Array<Stone?>,
        size: BoardSize
    ): Map<Int, Stone> {
        val groups = computeGroups(board, size)
        val byPoint = HashMap<Int, GroupInfo>()
        for (g in groups) for (p in g.points) byPoint[p] = g
        val out = HashMap<Int, Stone>()
        for (p in board.indices) {
            if (board[p] != null) continue
            var koStone: GroupInfo? = null
            var ok = true
            val colors = HashSet<Stone>()
            for (nb in neighbors(p, size)) {
                val s = board[nb]
                if (s == null) {
                    ok = false
                    break
                }
                colors.add(s)
                val g = byPoint[nb] ?: continue
                if (g.points.size == 1 && g.liberties.size == 1 &&
                    g.liberties.contains(p)
                ) {
                    if (koStone != null && koStone !== g) {
                        ok = false
                        break
                    }
                    koStone = g
                }
            }
            if (!ok || koStone == null || colors.size != 1) continue
            out[p] = koStone!!.color
        }
        return out
    }

    private fun koMarks(
        size: BoardSize,
        moves: List<Move>,
        boards: List<Array<Stone?>>
    ): List<DecisiveMark> {
        val out = ArrayList<DecisiveMark>()
        var prev = koPoints(boards[0], size)
        for (t in 1..moves.size) {
            val cur = koPoints(boards[t], size)
            val mv = moves[t - 1]
            if (!mv.isPass) {
                val vanished = prev.keys.filter { it !in cur }
                if (vanished.isNotEmpty()) {
                    val moveIdx = mv.y * size.width + mv.x
                    // Playing ON the ko point with the colour
                    // opposite the ko stone is a recapture: the ko
                    // flips, it is not resolved.
                    val recapture = vanished.any { p ->
                        p == moveIdx && prev[p] == mv.stone.other()
                    }
                    if (!recapture) {
                        out.add(
                            DecisiveMark(
                                t, DecisiveKind.KO,
                                DecisiveReason.KO_RESOLVED
                            )
                        )
                    }
                }
            }
            prev = cur
        }
        return out
    }
}
