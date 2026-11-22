package com.tuxungo.android

import android.content.Context

/** User-adjustable app settings, persisted in SharedPreferences. */
data class AppSettings(
    val soundEnabled: Boolean = true,
    val placeSound: String = "original",
    val captureSound: String = "real",
    val boardSkin: String = "beige",
    /** Board stretches to the screen edges (coordinates hidden). */
    val boardFill: Boolean = false,
    /** Enlarged 5x5 placement magnifier while confirming a move. */
    val magnifier: Boolean = false,
    /** Continuous analysis scope: both / B / W (side to move). */
    val autoAnalysisSide: String = "both",
    /** Show the human-style model's top two moves as orange marks. */
    val humanMarks: Boolean = false,
    /** Show only the AI's top three candidate moves on the board. */
    val topThreeOnly: Boolean = false,
    val confirmMove: Boolean = true,
    val showCoordinates: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** AI-vs-AI spectating pace: fast / normal / slow. */
    val eveSpeed: String = "normal",
    /** Theme accent: sakura / matcha / indigo. */
    val accent: String = "sakura",
    /** Appearance: system / light / dark. */
    val themeMode: String = "system",
    /** Visits for the silent per-move background analysis during games. */
    val autoVisits: Int = 80,
    /** Keep the screen awake while a game is on screen. */
    val keepScreenOn: Boolean = false,
    /** Draw move numbers on the stones during a game. */
    val showMoveNumbers: Boolean = false,
    /** Smart referee: auto-score a fully settled game. */
    val refereeEnabled: Boolean = false,
    /**
     * Deliberate blunders: a full-strength AI that is safely winning
     * occasionally (1-2 times per game) plays a clearly weaker move on
     * purpose, marked as deliberate in the record. Off by default.
     */
    val aiBlunders: Boolean = false,
    /** Save the final position image to the gallery when a game ends. */
    val autoSaveImage: Boolean = false,
    // ---- Position judgment (形势判断) tuning ----
    /** Visits for the in-game judgment / scoring analysis. */
    val judgeVisits: Int = 800,
    /** Territory colouring starts at this ownership strength (0.05..0.6):
     *  higher = only settled territory is coloured (less "spread"). */
    val territorySpread: Float = 0.30f,
    /** Territory colouring strength multiplier (0.4..1.0). */
    val territoryIntensity: Float = 1.0f,
    /** Dead-stone strictness: conservative / standard / bold. */
    val deadStrictness: String = "standard",
    /** Judgment panel: show the per-side count breakdown lines. */
    val judgeShowDetail: Boolean = true,
    /** Judgment panel: show the black/white score proportion bar. */
    val judgeShowBar: Boolean = true,
    /** Judgment panel: show the confidence line. */
    val judgeShowConfidence: Boolean = true
)

object SettingsStore {
    private const val PREFS = "tuxungo_settings"

    val PLACE_SOUNDS = listOf(
        "original" to "最初版（最早的落子声）",
        "real" to "真实录音（真棋盘落子·随机）",
        "classic" to "经典落子（仿主流棋盘）",
        "crisp" to "清脆",
        "wood" to "木质沉厚",
        "soft" to "轻柔"
    )

    val CAPTURE_SOUNDS = listOf(
        "original" to "最初版（最早的提子声）",
        "real" to "真实录音（真提子声·随机）",
        "double" to "提子双击",
        "heavy" to "提子沉稳"
    )

    val AUTO_VISITS = listOf(
        40 to "快速",
        80 to "标准",
        160 to "深度"
    )

    val ACCENTS = listOf(
        "sakura" to "樱花粉",
        "matcha" to "抹茶绿",
        "indigo" to "靛蓝"
    )

    val THEME_MODES = listOf(
        "system" to "跟随系统",
        "light" to "浅色",
        "dark" to "深色"
    )

    val EVE_SPEEDS = listOf(
        "fast" to "快",
        "normal" to "中",
        "slow" to "慢"
    )

    val JUDGE_VISITS = listOf(
        100 to "极速",
        300 to "快速",
        800 to "标准",
        1500 to "精准",
        3000 to "深算"
    )

    val DEAD_STRICTNESS = listOf(
        "verycareful" to "非常保守",
        "conservative" to "保守（拿不准就不判死）",
        "standard" to "标准",
        "bold" to "大胆（更积极判死）",
        "verybold" to "非常大胆"
    )

    /** Group-mean ownership (against the owner) above which a group is
     *  judged dead, per strictness setting. */
    fun deadGroupMean(strictness: String): Double = when (strictness) {
        "verycareful" -> 0.55
        "conservative" -> 0.45
        "bold" -> 0.25
        "verybold" -> 0.15
        else -> 0.35
    }

    val BOARD_SKINS = listOf(
        "beige" to "米黄木纹（默认）",
        "kaya" to "榧木实纹",
        "wood_light" to "榧木纹（浅）",
        "wood_dark" to "胡桃木（深）",
        "bamboo" to "竹黄",
        "sakura" to "樱花粉",
        "moon" to "月白石",
        "obsidian" to "玄黑石墨"
    )

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // One-time 2.51 defaults: the beige board becomes the default
        // skin (anyone still on a previous default — kaya / wood_light —
        // is moved over once; an explicit later pick is respected) and
        // the placement sound returns to the app's very first one.
        if (!p.getBoolean("defaults251", false)) {
            val e = p.edit()
            val skin = p.getString("boardSkin", "kaya") ?: "kaya"
            if (skin == "kaya" || skin == "wood_light") {
                e.putString("boardSkin", "beige")
            }
            e.putString("placeSound", "original")
            e.putBoolean("defaults251", true)
            e.apply()
        }
        return AppSettings(
            soundEnabled = p.getBoolean("soundEnabled", true),
            placeSound = p.getString("placeSound", "original") ?: "original",
            captureSound = p.getString("captureSound", "real") ?: "real",
            boardSkin = p.getString("boardSkin", "beige") ?: "beige",
            boardFill = p.getBoolean("boardFill", false),
            magnifier = p.getBoolean("magnifier", false),
            autoAnalysisSide = p.getString("autoAnalysisSide", "both") ?: "both",
            humanMarks = p.getBoolean("humanMarks", false),
            topThreeOnly = p.getBoolean("topThreeOnly", false),
            confirmMove = p.getBoolean("confirmMove", true),
            showCoordinates = p.getBoolean("showCoordinates", true),
            vibrationEnabled = p.getBoolean("vibrationEnabled", true),
            eveSpeed = p.getString("eveSpeed", "normal") ?: "normal",
            accent = p.getString("accent", "sakura") ?: "sakura",
            themeMode = p.getString("themeMode", "system") ?: "system",
            autoVisits = p.getInt("autoVisits", 80),
            keepScreenOn = p.getBoolean("keepScreenOn", false),
            showMoveNumbers = p.getBoolean("showMoveNumbers", false),
            refereeEnabled = p.getBoolean("refereeEnabled", false),
            aiBlunders = p.getBoolean("aiBlunders", false),
            autoSaveImage = p.getBoolean("autoSaveImage", false),
            judgeVisits = p.getInt("judgeVisits", 800),
            territorySpread = p.getFloat("territorySpread", 0.30f),
            territoryIntensity = p.getFloat("territoryIntensity", 1.0f),
            deadStrictness = p.getString("deadStrictness", "standard") ?: "standard",
            judgeShowDetail = p.getBoolean("judgeShowDetail", true),
            judgeShowBar = p.getBoolean("judgeShowBar", true),
            judgeShowConfidence = p.getBoolean("judgeShowConfidence", true)
        )
    }

    fun save(context: Context, s: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("soundEnabled", s.soundEnabled)
            .putString("placeSound", s.placeSound)
            .putString("captureSound", s.captureSound)
            .putString("boardSkin", s.boardSkin)
            .putBoolean("boardFill", s.boardFill)
            .putBoolean("magnifier", s.magnifier)
            .putString("autoAnalysisSide", s.autoAnalysisSide)
            .putBoolean("humanMarks", s.humanMarks)
            .putBoolean("topThreeOnly", s.topThreeOnly)
            .putBoolean("confirmMove", s.confirmMove)
            .putBoolean("showCoordinates", s.showCoordinates)
            .putBoolean("vibrationEnabled", s.vibrationEnabled)
            .putString("eveSpeed", s.eveSpeed)
            .putString("accent", s.accent)
            .putString("themeMode", s.themeMode)
            .putInt("autoVisits", s.autoVisits)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("showMoveNumbers", s.showMoveNumbers)
            .putBoolean("refereeEnabled", s.refereeEnabled)
            .putBoolean("aiBlunders", s.aiBlunders)
            .putBoolean("autoSaveImage", s.autoSaveImage)
            .putInt("judgeVisits", s.judgeVisits)
            .putFloat("territorySpread", s.territorySpread)
            .putFloat("territoryIntensity", s.territoryIntensity)
            .putString("deadStrictness", s.deadStrictness)
            .putBoolean("judgeShowDetail", s.judgeShowDetail)
            .putBoolean("judgeShowBar", s.judgeShowBar)
            .putBoolean("judgeShowConfidence", s.judgeShowConfidence)
            .apply()
    }

    fun placeSoundRes(id: String): Int = when (id) {
        "original" -> R.raw.stone_place
        "crisp" -> R.raw.snd_place_crisp
        "wood" -> R.raw.snd_place_wood
        "soft" -> R.raw.snd_place_soft
        else -> R.raw.snd_place_classic
    }

    fun captureSoundRes(id: String): Int = when (id) {
        "original" -> R.raw.stone_capture
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
