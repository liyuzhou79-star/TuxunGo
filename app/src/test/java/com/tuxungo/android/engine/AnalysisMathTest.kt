package com.tuxungo.android.engine

import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun ownershipBlackToMoveUnchanged() {
        // KataGo ownership is side-to-move perspective: with Black to
        // move, positive already means Black owns the point.
        val raw = doubleArrayOf(0.9, -0.8, 0.1)
        val out = AnalysisMath.ownershipToBlack(raw, Stone.BLACK)
        assertEquals(0.9, out[0], 1e-9)
        assertEquals(-0.8, out[1], 1e-9)
        assertEquals(0.1, out[2], 1e-9)
    }

    @Test
    fun ownershipWhiteToMoveNegated() {
        // With White to move, a positive raw value means WHITE owns the
        // point, so in Black's perspective it must become negative.
        // (Verified against real KataGo: drive5/drive6 corner test.)
        val raw = doubleArrayOf(0.83, -0.77)
        val out = AnalysisMath.ownershipToBlack(raw, Stone.WHITE)
        assertEquals(-0.83, out[0], 1e-9)
        assertEquals(0.77, out[1], 1e-9)
    }

    // isDecided gates the 「绝」 mark on the analysis top move.
    // 19x19: area 361, so the move gate is 361/6 = 60 moves and the
    // empty gate is 361*4/5 = 288 points.

    @Test
    fun decidedWhenBlackCrushingLate() {
        assertTrue(AnalysisMath.isDecided(0.995, 15.0, 200, 150, 361))
    }

    @Test
    fun decidedWhenWhiteCrushingLate() {
        assertTrue(AnalysisMath.isDecided(0.005, -12.0, 180, 170, 361))
    }

    @Test
    fun decidedAtExactThresholds() {
        assertTrue(AnalysisMath.isDecided(0.99, 10.0, 60, 288, 361))
    }

    @Test
    fun notDecidedWhenWinrateShortOfExtreme() {
        assertFalse(AnalysisMath.isDecided(0.98, 20.0, 200, 150, 361))
    }

    @Test
    fun notDecidedWhenLeadTooSmall() {
        assertFalse(AnalysisMath.isDecided(0.999, 5.0, 200, 150, 361))
    }

    @Test
    fun notDecidedInOpening() {
        // A huge early lead (a big capture) is not 「绝」 yet: the
        // game has not left its opening stretch.
        assertFalse(AnalysisMath.isDecided(0.999, 30.0, 30, 200, 361))
    }

    @Test
    fun notDecidedWhenBoardStillWideOpen() {
        assertFalse(AnalysisMath.isDecided(0.999, 30.0, 100, 300, 361))
    }

    @Test
    fun decidedOnSmallBoard() {
        // 9x9: area 81, move gate 13, empty gate 64.
        assertTrue(AnalysisMath.isDecided(0.995, 11.0, 40, 30, 81))
        assertFalse(AnalysisMath.isDecided(0.995, 11.0, 10, 30, 81))
    }

    @Test
    fun notDecidedForDegenerateArea() {
        assertFalse(AnalysisMath.isDecided(0.999, 30.0, 100, 0, 0))
    }
}
