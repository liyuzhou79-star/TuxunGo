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
        val r = TerritoryEstimate.estimate(ownership, stones)
        assertEquals(1, r.blackTerritory)
        assertEquals(1, r.whiteTerritory)
        assertEquals(1, r.neutral)
    }
}
