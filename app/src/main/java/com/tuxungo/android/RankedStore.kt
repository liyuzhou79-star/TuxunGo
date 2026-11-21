package com.tuxungo.android

import android.content.Context

/**
 * Win/loss tallies against each ranked AI level, shown on the
 * career stats page. Stored as "rank=wins-losses" pairs joined
 * by ";". Rank ids never contain ";" or "=".
 */
object RankedStore {
    private const val PREFS = "ranked_prefs"
    private const val KEY = "tallies"

    fun record(context: Context, rank: String, humanWon: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val map = parse(prefs.getString(KEY, "") ?: "")
        val cur = map[rank] ?: intArrayOf(0, 0)
        if (humanWon) cur[0]++ else cur[1]++
        map[rank] = cur
        prefs.edit().putString(
            KEY,
            map.entries.joinToString(";") { (r, wl) ->
                "$r=${wl[0]}-${wl[1]}"
            }
        ).apply()
    }

    fun loadAll(context: Context): Map<String, IntArray> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return parse(prefs.getString(KEY, "") ?: "")
    }

    internal fun parse(text: String): MutableMap<String, IntArray> {
        val out = mutableMapOf<String, IntArray>()
        for (part in text.split(";")) {
            val kv = part.split("=")
            if (kv.size != 2) continue
            val wl = kv[1].split("-")
            if (wl.size != 2) continue
            out[kv[0]] = intArrayOf(
                wl[0].toIntOrNull() ?: 0,
                wl[1].toIntOrNull() ?: 0
            )
        }
        return out
    }
}
