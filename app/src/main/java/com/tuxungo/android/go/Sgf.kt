package com.tuxungo.android.go

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Minimal SGF (FF[4]) export for finished or in-progress games. */
object Sgf {

    private fun coord(x: Int): Char = 'a' + x

    fun export(
        game: GoGame,
        blackName: String = "",
        whiteName: String = "",
        setupStones: List<Move> = emptyList(),
        startPlayer: Stone = Stone.BLACK,
        /** Optional per-move comments (1-based move number), e.g. review notes. */
        comments: Map<Int, String> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append("(;GM[1]FF[4]CA[UTF-8]AP[TuxunGo:1.0.4]")
        if (game.size.isSquare) sb.append("SZ[").append(game.size.width).append("]")
        else sb.append("SZ[").append(game.size.width).append(':').append(game.size.height).append("]")
        sb.append("KM[").append(game.komi).append("]")
        sb.append("DT[").append(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())).append("]")
        if (blackName.isNotEmpty()) sb.append("PB[").append(blackName).append("]")
        if (whiteName.isNotEmpty()) sb.append("PW[").append(whiteName).append("]")
        if (game.handicapStones.isNotEmpty()) {
            sb.append("HA[").append(game.handicapStones.size).append("]")
            sb.append("AB")
            for ((x, y) in game.handicapStones) {
                sb.append('[').append(coord(x)).append(coord(y)).append(']')
            }
        }
        if (setupStones.isNotEmpty()) {
            // Custom position （摆棋）: AB/AW setup plus who moves first.
            val blacks = setupStones.filter { it.stone == Stone.BLACK }
            val whites = setupStones.filter { it.stone == Stone.WHITE }
            if (blacks.isNotEmpty()) {
                sb.append("AB")
                for (m in blacks) sb.append('[').append(coord(m.x)).append(coord(m.y)).append(']')
            }
            if (whites.isNotEmpty()) {
                sb.append("AW")
                for (m in whites) sb.append('[').append(coord(m.x)).append(coord(m.y)).append(']')
            }
            sb.append("PL[").append(if (startPlayer == Stone.BLACK) "B" else "W").append("]")
        }
        for ((index, move) in game.moves.withIndex()) {
            sb.append(if (move.stone == Stone.BLACK) ";B[" else ";W[")
            if (move.isPass) sb.append("tt")
            else sb.append(coord(move.x)).append(coord(move.y))
            sb.append("]")
            val comment = comments[index + 1]
            if (!comment.isNullOrBlank()) {
                val escaped = comment.replace("\\", "\\\\").replace("]", "\\]")
                sb.append("C[").append(escaped).append("]")
            }
        }
        if (game.finished) {
            val result = when {
                game.timeoutBy != null ->
                    if (game.timeoutBy == Stone.BLACK) "W+T" else "B+T"
                game.resignedBy != null ->
                    if (game.resignedBy == Stone.BLACK) "W+R" else "B+R"
                else -> {
                    val s = game.scoringDead?.let { game.scoreWithDead(it) } ?: game.score()
                    val diff = kotlin.math.abs(s.lead)
                    val text = if (diff % 1.0 == 0.0) diff.toInt().toString() else diff.toString()
                    if (s.lead > 0) "B+$text" else "W+$text"
                }
            }
            sb.append("RE[").append(result).append("]")
        }
        sb.append(')')
        return sb.toString()
    }
}
