package com.tuxungo.android.engine

import android.content.Context
import com.tuxungo.android.go.Stone

/** Per-side AI configuration: which model plays a color, how hard it
 *  thinks, and whether it imitates human move style. */
data class SideAiConfig(
    val modelId: String,
    val visits: Int,
    val humanLike: Boolean = false
)

/**
 * Persists the per-side AI settings so Black and White can run
 * different models at different strengths, in any game, and the
 * choices survive restarts.
 */
object SideAiStore {

    private const val PREFS = "tuxungo_sideai"

    private fun key(stone: Stone, part: String) =
        (if (stone == Stone.BLACK) "black" else "white") + "_" + part

    fun load(context: Context, stone: Stone, defaultVisits: Int): SideAiConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val modelId = prefs.getString(key(stone, "model"), null)
            ?: ModelStore.activeId(context)
        val visits = prefs.getInt(key(stone, "visits"), defaultVisits)
        val humanLike = prefs.getBoolean(key(stone, "human"), false)
        return SideAiConfig(modelId, visits, humanLike)
    }

    fun save(context: Context, stone: Stone, cfg: SideAiConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(stone, "model"), cfg.modelId)
            .putInt(key(stone, "visits"), cfg.visits)
            .putBoolean(key(stone, "human"), cfg.humanLike)
            .apply()
    }
}
