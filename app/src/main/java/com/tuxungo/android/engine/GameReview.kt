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
    val deliberate: Boolean = false
)

enum class MarkKind { BLUNDER, BRILLIANT, KEY, DELIBERATE }

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
        val deliberateMoves: List<Int> = emptyList()
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

    /** "wr,lead,bestX,bestY,gap;..." — best is -1,-1 for pass / unknown. */
    fun encodeTurns(turns: List<TurnStat>): String =
        turns.joinToString(";") { t ->
            val bx = t.bestMove?.x ?: -1
            val by = t.bestMove?.y ?: -1
            val base = "${fmt(t.winrateBlack)},${fmt(t.scoreLeadBlack)}," +
                "$bx,$by,${fmt(t.bestGap)},${t.bestPv}"
            // 7th field, present only when set, so old files and old
            // readers are unaffected.
            if (t.deliberate) "$base,1" else base
        }

    fun decodeTurns(text: String): List<TurnStat> {
        if (text.isBlank()) return emptyList()
        return text.split(";").mapIndexed { idx, part ->
            val f = part.split(",")
            val bx = f.getOrNull(2)?.toIntOrNull() ?: -1
            val by = f.getOrNull(3)?.toIntOrNull() ?: -1
            TurnStat(
                turn = idx,
                winrateBlack = f.getOrNull(0)?.toDoubleOrNull() ?: 0.5,
                scoreLeadBlack = f.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                bestMove = null,
                bestIsPass = bx < 0,
                bestGap = f.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                bestPv = f.getOrNull(5) ?: "",
                deliberate = f.getOrNull(6) == "1"
            ).let { base ->
                // bestMove's stone is not stored; marks() only needs its
                // coordinates, so park them on a throwaway Move.
                if (bx >= 0 && by >= 0) {
                    base.copy(bestMove = Move(Stone.BLACK, bx, by))
                } else {
                    base
                }
            }
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
            val bx = t.bestMove?.x ?: -1
            val by = t.bestMove?.y ?: -1
            val base = "$turn=${fmt(t.winrateBlack)},${fmt(t.scoreLeadBlack)}," +
                "$bx,$by,${fmt(t.bestGap)},${t.bestPv}"
            if (t.deliberate) "$base,1" else base
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
            val f = body.split(",")
            val bx = f.getOrNull(2)?.toIntOrNull() ?: -1
            val by = f.getOrNull(3)?.toIntOrNull() ?: -1
            out[turn] = TurnStat(
                turn = turn,
                winrateBlack = f.getOrNull(0)?.toDoubleOrNull() ?: 0.5,
                scoreLeadBlack = f.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                bestMove = if (bx >= 0 && by >= 0) Move(Stone.BLACK, bx, by) else null,
                bestIsPass = bx < 0,
                bestGap = f.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                bestPv = f.getOrNull(5) ?: "",
                deliberate = f.getOrNull(6) == "1"
            )
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
