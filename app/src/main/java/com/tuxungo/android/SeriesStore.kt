package com.tuxungo.android

import android.content.Context

/**
 * Tally for an AI-vs-AI series (first to N wins, colours swap
 * after every game). Keyed by the unordered model pairing, so a
 * different matchup starts a fresh tally automatically.
 */
object SeriesStore {

    private const val PREFS = "series_store"

    data class Tally(
        val nameA: String,
        val winsA: Int,
        val nameB: String,
        val winsB: Int,
        val games: Int
    )

    fun current(context: Context): Tally? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val a = p.getString("nameA", null) ?: return null
        val b = p.getString("nameB", null) ?: return null
        return Tally(
            a, p.getInt("winsA", 0),
            b, p.getInt("winsB", 0),
            p.getInt("games", 0)
        )
    }

    /**
     * Record one finished game and return the updated tally. If
     * the pairing differs from the stored one, a fresh tally
     * starts with this game (model A = the one that played Black
     * in the first game).
     */
    fun record(
        context: Context,
        blackName: String,
        whiteName: String,
        blackWon: Boolean
    ): Tally {
        val cur = current(context)
        val tally = if (cur == null ||
            setOf(cur.nameA, cur.nameB) != setOf(blackName, whiteName)
        ) {
            Tally(
                blackName, if (blackWon) 1 else 0,
                whiteName, if (blackWon) 0 else 1,
                1
            )
        } else {
            val winner = if (blackWon) blackName else whiteName
            cur.copy(
                winsA = cur.winsA + if (winner == cur.nameA) 1 else 0,
                winsB = cur.winsB + if (winner == cur.nameB) 1 else 0,
                games = cur.games + 1
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("nameA", tally.nameA)
            .putInt("winsA", tally.winsA)
            .putString("nameB", tally.nameB)
            .putInt("winsB", tally.winsB)
            .putInt("games", tally.games)
            .apply()
        return tally
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
