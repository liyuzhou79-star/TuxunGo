package com.tuxungo.android.engine

import android.content.Context
import com.tuxungo.android.go.Stone

/** Per-side AI configuration: which model plays a color, how hard it
 *  thinks, and whether it imitates human move style. */
data class SideAiConfig(
    val modelId: String,
    val visits: Int,
    val humanLike: Boolean = false,
    /** Rank the AI is capped at: "max" (full strength), "9d".."1d"
     *  or "1k".."18k". A capped rank plays pure imitation of the
     *  human model at that rank — genuinely that strong, mistakes
     *  included, not a full-strength engine throwing moves away. */
    val rank: String = "max"
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
        val rank = prefs.getString(key(stone, "rank"), "max") ?: "max"
        return SideAiConfig(modelId, visits, humanLike, rank)
    }

    fun save(context: Context, stone: Stone, cfg: SideAiConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(stone, "model"), cfg.modelId)
            .putInt(key(stone, "visits"), cfg.visits)
            .putBoolean(key(stone, "human"), cfg.humanLike)
            .putString(key(stone, "rank"), cfg.rank)
            .apply()
    }

    /** Selectable AI ranks, strongest first: Max, then dan grades,
     *  then kyu grades down to 18k (the user's chosen floor). */
    val RANKS: List<String> =
        listOf("max") +
            (9 downTo 1).map { "${it}d" } +
            (1..18).map { "${it}k" }

    /** Display label for a rank value, e.g. "max" -> "Max（最强）". */
    fun rankLabel(rank: String): String = when {
        rank == "max" -> "Max（最强）"
        rank.endsWith("d") -> rank.dropLast(1) + " 段"
        rank.endsWith("k") -> rank.dropLast(1) + " 级"
        else -> rank
    }

    /** The human-model profile string for a rank, null for Max. */
    fun rankProfile(rank: String): String? =
        if (rank == "max") null else "rank_$rank"
}
