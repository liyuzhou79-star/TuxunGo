package com.tuxungo.android

import android.content.Context

/** User-adjustable app settings, persisted in SharedPreferences. */
data class AppSettings(
    val soundEnabled: Boolean = true,
    val placeSound: String = "classic",
    val captureSound: String = "double",
    val boardSkin: String = "wood_light",
    val confirmMove: Boolean = true,
    val showCoordinates: Boolean = true
)

object SettingsStore {
    private const val PREFS = "tuxungo_settings"

    val PLACE_SOUNDS = listOf(
        "classic" to "经典落子（仿主流棋盘）",
        "crisp" to "清脆",
        "wood" to "木质沉厚",
        "soft" to "轻柔"
    )

    val CAPTURE_SOUNDS = listOf(
        "double" to "提子双击",
        "heavy" to "提子沉稳"
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
            placeSound = p.getString("placeSound", "classic") ?: "classic",
            captureSound = p.getString("captureSound", "double") ?: "double",
            boardSkin = p.getString("boardSkin", "wood_light") ?: "wood_light",
            confirmMove = p.getBoolean("confirmMove", true),
            showCoordinates = p.getBoolean("showCoordinates", true)
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
}
