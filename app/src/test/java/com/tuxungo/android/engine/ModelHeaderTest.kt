package com.tuxungo.android.engine

import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelHeaderTest {

    @Test
    fun parsesRealKataGoHeader() {
        // Head of kata1-b10c128-s1141046784-d204142634.txt (real file).
        val head = """
            b10c128-s1141046784-d204142634
            8
            22
            19
            trunk
            10
            128
            128
            96
        """.trimIndent()
        val header = ModelHeaderParser.parse(head)
        assertEquals("b10c128-s1141046784-d204142634", header?.name)
        assertEquals(10, header?.numBlocks)
        assertEquals(128, header?.channels)
        assertEquals("10 层 · 128 通道", header?.describe())
    }

    @Test
    fun rejectsBinaryGarbage() {
        assertNull(ModelHeaderParser.parse(""))
        assertNull(ModelHeaderParser.parse("���� binary not a model name with spaces"))
    }

    @Test
    fun territoryEstimateCountsEmptyPointsOnly() {
        val ownership = doubleArrayOf(0.9, -0.9, 0.2, 0.8)
        val stones = arrayOf<Stone?>(null, null, null, Stone.BLACK)
        val r = TerritoryEstimate.estimate(ownership, stones, 2, 2)
        assertEquals(1, r.blackTerritory)
        assertEquals(1, r.whiteTerritory)
        assertEquals(1, r.neutral)
    }

    @Test
    fun deadGroupJudgedByMeanNotSinglePoints() {
        // 3x3: two black stones whose points lean White on average
        // (dead group), everything else solidly White's.
        val ownership = doubleArrayOf(
            -0.6, -0.5, -0.8,
            -0.8, -0.8, -0.8,
            -0.8, -0.8, -0.8
        )
        val stones = arrayOf<Stone?>(
            Stone.BLACK, Stone.BLACK, null,
            null, null, null,
            null, null, null
        )
        val dead = TerritoryEstimate.deadStones(ownership, stones, 3, 3)
        assertEquals(setOf(0, 1), dead)
        val r = TerritoryEstimate.estimate(ownership, stones, 3, 3)
        assertEquals(0, r.blackTerritory)
        assertEquals(9, r.whiteTerritory)
        assertEquals(0, r.neutral)
    }

    @Test
    fun noisyPointDoesNotKillLiveGroup() {
        // One point of a live black group reads -0.6 (noise); the
        // group mean keeps it alive. A lone stone at -0.95 is dead
        // on its own certainty even inside the same group shape.
        val ownership = doubleArrayOf(0.9, -0.6, -0.95)
        val stones = arrayOf<Stone?>(Stone.BLACK, Stone.BLACK, Stone.BLACK)
        val dead = TerritoryEstimate.deadStones(ownership, stones, 3, 1)
        assertEquals(setOf(2), dead)
    }

    @Test
    fun solidRegionCountsInFullDespiteWeakPoint() {
        // A black region with one weak point inside (0.3): the region
        // mean carries it, so all four points count for Black.
        val ownership = doubleArrayOf(0.9, 0.9, 0.9, 0.3)
        val stones = arrayOf<Stone?>(null, null, null, null)
        val r = TerritoryEstimate.estimate(ownership, stones, 2, 2)
        assertEquals(4, r.blackTerritory)
        assertEquals(0, r.neutral)
    }

    @Test
    fun displayOwnershipDeepensSettledEnclosedRegion() {
        // 5x5: a White wall encloses the top-left 2x2 corner. The
        // engine reads the corner lukewarm (-0.45); for display it
        // must come out decisively White. One point leaning Black
        // (an invasion spot) keeps its raw value.
        val stones = arrayOfNulls<Stone>(25)
        for ((x, y) in listOf(2 to 0, 2 to 1, 0 to 2, 1 to 2)) {
            stones[y * 5 + x] = Stone.WHITE
        }
        val ownership = DoubleArray(25)
        ownership[0] = -0.45
        ownership[1] = -0.45
        ownership[5] = -0.45
        ownership[6] = 0.5
        val out = TerritoryEstimate.displayOwnership(ownership, stones, 5, 5)
        assertTrue(out[0] < -0.7)
        assertTrue(out[1] < -0.7)
        assertTrue(out[5] < -0.7)
        assertEquals(0.5, out[6], 0.0001)
    }

    @Test
    fun displayOwnershipLeavesContestedAndWeakRegionsAlone() {
        // Contested: the empty area touches both colours.
        val stones = arrayOfNulls<Stone>(9)
        stones[0] = Stone.BLACK
        stones[2] = Stone.WHITE
        val ownership = DoubleArray(9) { 0.4 }
        val out = TerritoryEstimate.displayOwnership(ownership, stones, 3, 3)
        assertEquals(0.4, out[4], 0.0001)
        // Enclosed by White but the mean is too weak to call settled.
        val stones2 = arrayOfNulls<Stone>(25)
        for ((x, y) in listOf(2 to 0, 2 to 1, 0 to 2, 1 to 2)) {
            stones2[y * 5 + x] = Stone.WHITE
        }
        val weak = DoubleArray(25) { -0.1 }
        val out2 = TerritoryEstimate.displayOwnership(weak, stones2, 5, 5)
        assertEquals(-0.1, out2[0], 0.0001)
    }
}
