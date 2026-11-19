package com.tuxungo.android.engine

import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisMathTest {

    @Test
    fun blackToMoveKeepsValues() {
        val (wr, lead) = AnalysisMath.toBlackPerspective(0.6, 3.0, Stone.BLACK)
        assertEquals(0.6, wr, 1e-9)
        assertEquals(3.0, lead, 1e-9)
    }

    @Test
    fun whiteToMoveFlipsValues() {
        // White to move and White is doing well: winrate 0.7 for the mover
        // means Black's win rate is 0.3 and Black trails by 5 points.
        val (wr, lead) = AnalysisMath.toBlackPerspective(0.7, 5.0, Stone.WHITE)
        assertEquals(0.3, wr, 1e-9)
        assertEquals(-5.0, lead, 1e-9)
    }
}
