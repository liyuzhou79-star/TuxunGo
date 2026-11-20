package com.tuxungo.android

import android.content.Context
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import com.tuxungo.android.ui.GameConfig
import com.tuxungo.android.ui.GameMode

/**
 * Persists the in-progress game (setup + move list) so that switching to
 * the background, or the system killing the app, never loses the match:
 * on the next launch the game is restored exactly where it was.
 */
object GameStore {
    private const val PREFS = "tuxungo_game"

    data class SavedGame(
        val config: GameConfig,
        val moves: List<Move>,
        val finished: Boolean,
        /** Per-turn win-rate stats recorded live during the game. */
        val turnStats: Map<Int, com.tuxungo.android.engine.TurnStat> = emptyMap(),
        /** Remaining clock state, when the game uses a clock. */
        val clock: ClockSnapshot? = null,
        val timeoutBy: Stone? = null
    )

    /** Remaining time per side (main milliseconds, byoyomi periods and
     * the milliseconds left in the current byoyomi period). */
    data class ClockSnapshot(
        val blackMainMs: Long,
        val whiteMainMs: Long,
        val blackPeriods: Int,
        val whitePeriods: Int,
        val blackPeriodMs: Long,
        val whitePeriodMs: Long
    )

    fun save(
        context: Context,
        config: GameConfig,
        moves: List<Move>,
        finished: Boolean,
        turnStats: Map<Int, com.tuxungo.android.engine.TurnStat> = emptyMap(),
        clock: ClockSnapshot? = null,
        timeoutBy: Stone? = null
    ) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("exists", true)
            .putString("mode", config.mode.name)
            .putInt("width", config.width)
            .putInt("height", config.height)
            .putInt("handicap", config.handicap)
            .putFloat("komi", config.komi.toFloat())
            .putString("humanStone", config.humanStone.name)
            .putInt("aiVisits", config.aiVisits)
            .putString("startPlayer", config.startPlayer.name)
            .putString("customStones", encodeMoves(config.customStones))
            .putString("moves", encodeMoves(moves))
            .putBoolean("finished", finished)
            .putString(
                "turnStats",
                com.tuxungo.android.engine.ReviewMath.encodeSparse(turnStats)
            )
            .putInt("clockMinutes", config.clockMinutes)
            .putInt("seriesTarget", config.seriesTarget)
            .putString("timeoutBy", timeoutBy?.name ?: "")
        if (clock != null) {
            editor
                .putLong("clockBlackMain", clock.blackMainMs)
                .putLong("clockWhiteMain", clock.whiteMainMs)
                .putInt("clockBlackPeriods", clock.blackPeriods)
                .putInt("clockWhitePeriods", clock.whitePeriods)
                .putLong("clockBlackPeriod", clock.blackPeriodMs)
                .putLong("clockWhitePeriod", clock.whitePeriodMs)
        }
        editor.apply()
    }

    fun load(context: Context): SavedGame? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("exists", false)) return null
        val mode = runCatching { GameMode.valueOf(p.getString("mode", "PVP") ?: "PVP") }
            .getOrDefault(GameMode.PVP)
        val human = runCatching { Stone.valueOf(p.getString("humanStone", "BLACK") ?: "BLACK") }
            .getOrDefault(Stone.BLACK)
        val startPlayer = runCatching { Stone.valueOf(p.getString("startPlayer", "BLACK") ?: "BLACK") }
            .getOrDefault(Stone.BLACK)
        val clockMinutes = p.getInt("clockMinutes", 0)
        val config = GameConfig(
            mode = mode,
            width = p.getInt("width", 19),
            height = p.getInt("height", 19),
            handicap = p.getInt("handicap", 0),
            komi = p.getFloat("komi", 7.5f).toDouble(),
            humanStone = human,
            aiVisits = p.getInt("aiVisits", 200),
            customStones = decodeMoves(p.getString("customStones", "") ?: ""),
            startPlayer = startPlayer,
            clockMinutes = clockMinutes,
            seriesTarget = p.getInt("seriesTarget", 0)
        )
        val clock = if (clockMinutes > 0 && p.contains("clockBlackMain")) {
            ClockSnapshot(
                blackMainMs = p.getLong("clockBlackMain", clockMinutes * 60_000L),
                whiteMainMs = p.getLong("clockWhiteMain", clockMinutes * 60_000L),
                blackPeriods = p.getInt("clockBlackPeriods", 3),
                whitePeriods = p.getInt("clockWhitePeriods", 3),
                blackPeriodMs = p.getLong("clockBlackPeriod", 30_000L),
                whitePeriodMs = p.getLong("clockWhitePeriod", 30_000L)
            )
        } else {
            null
        }
        val timeoutBy = runCatching {
            Stone.valueOf(p.getString("timeoutBy", "") ?: "")
        }.getOrNull()
        return SavedGame(
            config = config,
            moves = decodeMoves(p.getString("moves", "") ?: ""),
            finished = p.getBoolean("finished", false),
            turnStats = com.tuxungo.android.engine.ReviewMath.decodeSparse(
                p.getString("turnStats", "") ?: ""
            ),
            clock = clock,
            timeoutBy = timeoutBy
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** "B3,4;W5,6;Bp" — color letter, then x,y or p for pass. */
    fun encodeMoves(moves: List<Move>): String =
        moves.joinToString(";") { m ->
            val c = if (m.stone == Stone.BLACK) "B" else "W"
            if (m.isPass) "${c}p" else "$c${m.x},${m.y}"
        }

    fun decodeMoves(s: String): List<Move> {
        if (s.isBlank()) return emptyList()
        return s.split(";").mapNotNull { token ->
            if (token.length < 2) return@mapNotNull null
            val stone = if (token[0] == 'B') Stone.BLACK else Stone.WHITE
            val rest = token.substring(1)
            if (rest == "p") {
                Move.pass(stone)
            } else {
                val parts = rest.split(",")
                val x = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val y = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                Move(stone, x, y)
            }
        }
    }
}
