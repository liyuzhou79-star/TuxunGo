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
}
