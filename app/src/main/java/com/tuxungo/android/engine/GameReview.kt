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
    val bestPv: String = ""
)

enum class MarkKind { BLUNDER, BRILLIANT, KEY }

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
        if (turns.size < 2 || moves.isEmpty()) return emptyList()
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

    /** "wr,lead,bestX,bestY,gap;..." — best is -1,-1 for pass / unknown. */
    fun encodeTurns(turns: List<TurnStat>): String =
        turns.joinToString(";") { t ->
            val bx = t.bestMove?.x ?: -1
            val by = t.bestMove?.y ?: -1
            "${fmt(t.winrateBlack)},${fmt(t.scoreLeadBlack)},$bx,$by,${fmt(t.bestGap)},${t.bestPv}"
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
                bestPv = f.getOrNull(5) ?: ""
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
            "$turn=${fmt(t.winrateBlack)},${fmt(t.scoreLeadBlack)},$bx,$by,${fmt(t.bestGap)},${t.bestPv}"
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
                bestPv = f.getOrNull(5) ?: ""
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
