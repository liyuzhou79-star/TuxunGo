package com.tuxungo.android

import android.content.Context

/** User-adjustable app settings, persisted in SharedPreferences. */
data class AppSettings(
    val soundEnabled: Boolean = true,
    val placeSound: String = "real",
    val captureSound: String = "real",
    val boardSkin: String = "wood_light",
    val confirmMove: Boolean = true,
    val showCoordinates: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** AI-vs-AI spectating pace: fast / normal / slow. */
    val eveSpeed: String = "normal",
    /** Theme accent: sakura / matcha / indigo. */
    val accent: String = "sakura"
)

object SettingsStore {
    private const val PREFS = "tuxungo_settings"

    val PLACE_SOUNDS = listOf(
        "real" to "真实录音（真棋盘落子·随机）",
        "classic" to "经典落子（仿主流棋盘）",
        "crisp" to "清脆",
        "wood" to "木质沉厚",
        "soft" to "轻柔"
    )

    val CAPTURE_SOUNDS = listOf(
        "real" to "真实录音（真提子声·随机）",
        "double" to "提子双击",
        "heavy" to "提子沉稳"
    )

    val ACCENTS = listOf(
        "sakura" to "樱花粉",
        "matcha" to "抹茶绿",
        "indigo" to "靛蓝"
    )

    val EVE_SPEEDS = listOf(
        "fast" to "快",
        "normal" to "中",
        "slow" to "慢"
    )

    val BOARD_SKINS = listOf(
        "wood_light" to "榧木纹（浅）",
        "wood_dark" to "胡桃木（深）",
        "bamboo" to "竹黄",
        "sakura" to "樱花粉"
    )

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            soundEnabled = p.getBoolean("soundEnabled", true),
            placeSound = p.getString("placeSound", "real") ?: "real",
            captureSound = p.getString("captureSound", "real") ?: "real",
            boardSkin = p.getString("boardSkin", "wood_light") ?: "wood_light",
            confirmMove = p.getBoolean("confirmMove", true),
            showCoordinates = p.getBoolean("showCoordinates", true),
            vibrationEnabled = p.getBoolean("vibrationEnabled", true),
            eveSpeed = p.getString("eveSpeed", "normal") ?: "normal",
            accent = p.getString("accent", "sakura") ?: "sakura"
        )
    }

    fun save(context: Context, s: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("soundEnabled", s.soundEnabled)
            .putString("placeSound", s.placeSound)
            .putString("captureSound", s.captureSound)
            .putString("boardSkin", s.boardSkin)
            .putBoolean("confirmMove", s.confirmMove)
            .putBoolean("showCoordinates", s.showCoordinates)
            .putBoolean("vibrationEnabled", s.vibrationEnabled)
            .putString("eveSpeed", s.eveSpeed)
            .putString("accent", s.accent)
            .apply()
    }

    fun placeSoundRes(id: String): Int = when (id) {
        "crisp" -> R.raw.snd_place_crisp
        "wood" -> R.raw.snd_place_wood
        "soft" -> R.raw.snd_place_soft
        else -> R.raw.snd_place_classic
    }

    fun captureSoundRes(id: String): Int = when (id) {
        "heavy" -> R.raw.snd_capture_heavy
        else -> R.raw.snd_capture_double
    }

    /** All variants for a sound choice; "real" is a set of true recordings, picked at random per play. */
    fun placeSoundResList(id: String): List<Int> = when (id) {
        "real" -> listOf(
            R.raw.snd_real_place0, R.raw.snd_real_place1, R.raw.snd_real_place2,
            R.raw.snd_real_place3, R.raw.snd_real_place4
        )
        else -> listOf(placeSoundRes(id))
    }

    fun captureSoundResList(id: String): List<Int> = when (id) {
        "real" -> listOf(
            R.raw.snd_real_capture0, R.raw.snd_real_capture1, R.raw.snd_real_capture2,
            R.raw.snd_real_capture3, R.raw.snd_real_capture4
        )
        else -> listOf(captureSoundRes(id))
    }
}
