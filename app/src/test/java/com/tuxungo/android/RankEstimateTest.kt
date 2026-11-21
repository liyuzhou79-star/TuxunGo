package com.tuxungo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Strength estimate: centre, range and data gates. */
class RankEstimateTest {

    @Test
    fun noDataNoEstimate() {
        assertNull(RankEstimate.estimate(emptyMap(), 0, 8))
        assertNull(RankEstimate.estimate(emptyMap(), 4, 8))
        // Games only against the unranked "max" AI do not count.
        assertNull(
            RankEstimate.estimate(mapOf("max" to intArrayOf(3, 0)), 0, 8)
        )
    }

    @Test
    fun tsumegoOnlyRoughEstimate() {
        val r = RankEstimate.estimate(emptyMap(), 8, 8)!!
        assertEquals("14k", r.lowRank)
        assertEquals("10k", r.highRank)
    }

    @Test
    fun beatenRankIsTheCentre() {
        val r = RankEstimate.estimate(
            mapOf("10k" to intArrayOf(2, 1)), 3, 8
        )!!
        assertEquals("12k", r.lowRank)
        assertEquals("8k", r.highRank)
    }

    @Test
    fun undefeatedStreakMovesCentreUp() {
        val r = RankEstimate.estimate(
            mapOf("3d" to intArrayOf(3, 0)), 0, 8
        )!!
        // Centre moves from 3d to 4d, range 6d .. 2d.
        assertEquals("2d", r.lowRank)
        assertEquals("6d", r.highRank)
    }

    @Test
    fun winlessSitsBelowWeakestTried() {
        val r = RankEstimate.estimate(
            mapOf("5k" to intArrayOf(0, 3)), 0, 8
        )!!
        // Centre 6k, range 8k .. 4k.
        assertEquals("8k", r.lowRank)
        assertEquals("4k", r.highRank)
    }
}
