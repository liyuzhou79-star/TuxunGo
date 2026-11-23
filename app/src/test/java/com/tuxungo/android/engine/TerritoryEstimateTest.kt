package com.tuxungo.android.engine

import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerritoryEstimateTest {

    private fun board(
        w: Int = 5,
        h: Int = 5,
        stones: Map<Int, Stone> = emptyMap(),
        ownership: DoubleArray = DoubleArray(w * h)
    ): Pair<Array<Stone?>, DoubleArray> {
        val arr = arrayOfNulls<Stone>(w * h)
        for ((i, s) in stones) arr[i] = s
        return arr to ownership
    }

    @Test
    fun emptyBoardWithNoOpinionIsAllNeutral() {
        val (stones, own) = board()
        val r = TerritoryEstimate.estimate(own, stones, 5, 5)
        assertEquals(0, r.blackTerritory)
        assertEquals(0, r.whiteTerritory)
        assertEquals(25, r.neutral)
        assertTrue(TerritoryEstimate.deadStones(own, stones, 5, 5).isEmpty())
    }

    @Test
    fun solidRegionCountsInFullForItsSide() {
        val (stones, _) = board()
        val black = TerritoryEstimate.estimate(
            DoubleArray(25) { 0.9 }, stones, 5, 5
        )
        assertEquals(25, black.blackTerritory)
        val white = TerritoryEstimate.estimate(
            DoubleArray(25) { -0.9 }, stones, 5, 5
        )
        assertEquals(25, white.whiteTerritory)
    }

    @Test
    fun deadStonePointCountsForTheOpponent() {
        // One white stone on a board Black owns everywhere: the
        // stone is dead and its point becomes Black territory.
        val (stones, own) = board(
            stones = mapOf(12 to Stone.WHITE),
            ownership = DoubleArray(25) { 0.9 }
        )
        assertEquals(setOf(12), TerritoryEstimate.deadStones(own, stones, 5, 5))
        val r = TerritoryEstimate.estimate(own, stones, 5, 5)
        assertEquals(25, r.blackTerritory)
        assertEquals(0, r.whiteTerritory)
    }

    @Test
    fun liveStoneIsNotTerritoryAndNotDead() {
        val own = DoubleArray(25)
        own[12] = 0.8
        val (stones, _) = board(stones = mapOf(12 to Stone.BLACK), ownership = own)
        assertTrue(TerritoryEstimate.deadStones(own, stones, 5, 5).isEmpty())
        val r = TerritoryEstimate.estimate(own, stones, 5, 5)
        assertEquals(0, r.blackTerritory)
        assertEquals(24, r.neutral)
    }

    @Test
    fun singleCertainStoneDiesInsideALiveGroup() {
        // Two connected black stones: one is overwhelmingly White's
        // (a cut-off tail), the other solidly Black's. The group
        // mean is not dead, but the certain stone dies alone.
        val own = DoubleArray(25)
        own[12] = -0.95
        own[13] = 0.5
        val (stones, _) = board(
            stones = mapOf(12 to Stone.BLACK, 13 to Stone.BLACK),
            ownership = own
        )
        assertEquals(setOf(12), TerritoryEstimate.deadStones(own, stones, 5, 5))
    }

    @Test
    fun displayOwnershipSharpensAnEnclosedRegion() {
        // Black ring around the centre point; the centre leans
        // Black mildly and gets pushed decisively toward Black.
        val own = DoubleArray(25)
        own[12] = 0.3
        val (stones, _) = board(
            stones = mapOf(
                7 to Stone.BLACK, 11 to Stone.BLACK,
                13 to Stone.BLACK, 17 to Stone.BLACK
            ),
            ownership = own
        )
        val out = TerritoryEstimate.displayOwnership(own, stones, 5, 5)
        assertEquals(0.795, out[12], 1e-9)
        // The open outer region has no lean: untouched.
        assertEquals(0.0, out[0], 1e-9)
        // The input array is never mutated.
        assertEquals(0.3, own[12], 1e-9)
    }
}
