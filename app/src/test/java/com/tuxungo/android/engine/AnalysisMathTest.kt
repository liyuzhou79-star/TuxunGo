package com.tuxungo.android.engine

import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // isDecided gates the 「绝」 call. New rule (2.63.0): the
    // leader's win rate must reach 99.4%, AND the game must be
    // beyond rescue — either the lead exceeds every empty point
    // left (the lock) or no undecided region larger than 12 points
    // remains.

    @Test
    fun decidedByLock() {
        // Lead 20 with only 15 empty points: win them all, still lose.
        assertTrue(AnalysisMath.isDecided(0.995, 20.0, 15, 40))
    }

    @Test
    fun decidedByNoBigRegionLeft() {
        // Lead 12, plenty of empty points, but all in tiny pockets.
        assertTrue(AnalysisMath.isDecided(0.995, 12.0, 60, 10))
    }

    @Test
    fun decidedForWhite() {
        assertTrue(AnalysisMath.isDecided(0.004, -25.0, 20, 99))
    }

    @Test
    fun notDecidedBelow994() {
        // 99% is not enough any more, even with a lock-sized lead.
        assertFalse(AnalysisMath.isDecided(0.99, 30.0, 10, 5))
        assertFalse(AnalysisMath.isDecided(0.9939, 30.0, 10, 5))
        assertTrue(AnalysisMath.isDecided(0.994, 30.0, 10, 5))
    }

    @Test
    fun notDecidedWhenComebackStillPossible() {
        // Win rate is there, but a 30-point undecided region remains
        // and the lead does not cover the empty points.
        assertFalse(AnalysisMath.isDecided(0.999, 12.0, 60, 30))
    }

    @Test
    fun notDecidedInOpening() {
        // Hundreds of empty points and one huge undecided area: an
        // early lead can never satisfy either rescue gate.
        assertFalse(AnalysisMath.isDecided(0.999, 30.0, 330, 330))
    }

    @Test
    fun largestRegionFindsBiggestPocket() {
        // 5x5, all empty. Ownership settled (+0.9) except an
        // undecided 2x2 block at the top-left and a single point.
        val own = DoubleArray(25) { 0.9 }
        val empty = BooleanArray(25) { true }
        for ((x, y) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, 4 to 4)) {
            own[y * 5 + x] = 0.1
        }
        assertEquals(
            4, AnalysisMath.largestUndecidedRegion(own, empty, 5, 5)
        )
    }

    @Test
    fun largestRegionIgnoresStonesAndSettledPoints() {
        val own = DoubleArray(9) { 0.0 }
        val empty = BooleanArray(9) { true }
        empty[4] = false // a stone splits the 3x3 into a ring of 8
        assertEquals(
            8, AnalysisMath.largestUndecidedRegion(own, empty, 3, 3)
        )
        assertEquals(
            0,
            AnalysisMath.largestUndecidedRegion(
                DoubleArray(9) { 0.9 }, BooleanArray(9) { true }, 3, 3
            )
        )
    }

    @Test
    fun unclearShareCountsOnlyUndecidedEmpties() {
        val own = doubleArrayOf(0.9, 0.1, -0.2, 0.8)
        val empty = booleanArrayOf(true, true, true, false)
        // 3 empty points, 2 undecided.
        assertEquals(
            2.0 / 3.0,
            AnalysisMath.unclearShare(own, empty),
            0.0001
        )
        assertEquals(
            0.0,
            AnalysisMath.unclearShare(own, BooleanArray(4)),
            0.0001
        )
    }

    @Test
    fun crossingsMarkLeadChanges() {
        assertEquals(
            listOf(2, 4),
            AnalysisMath.winrateCrossings(
                listOf(0.6, 0.55, 0.4, 0.45, 0.7)
            )
        )
        // Touching 0.5 exactly and turning back is not a crossing.
        assertEquals(
            emptyList<Int>(),
            AnalysisMath.winrateCrossings(listOf(0.6, 0.5, 0.6))
        )
        assertEquals(
            listOf(2),
            AnalysisMath.winrateCrossings(listOf(0.6, 0.5, 0.4))
        )
        assertEquals(
            emptyList<Int>(),
            AnalysisMath.winrateCrossings(listOf(0.3, 0.35, 0.2))
        )
    }

    private fun cand(
        x: Int, wrB: Double, leadB: Double, pass: Boolean = false
    ): Candidate = Candidate(
        move = if (pass) null else Move(Stone.BLACK, x, 0),
        vertex = if (pass) "pass" else "A1",
        visits = 100,
        winrateBlack = wrB,
        scoreLeadBlack = leadB,
        pvMoves = emptyList(),
        pvText = ""
    )

    @Test
    fun blunderPicksMostObviousSafeMistake() {
        // Black to move, root 95% / +20. Candidates: best (skip),
        // mild -3% (too quiet), obvious -10% staying safe, reckless
        // -30% breaking the win-rate floor.
        val cands = listOf(
            cand(0, 0.95, 20.0),
            cand(1, 0.92, 18.0),
            cand(2, 0.85, 13.0),
            cand(3, 0.65, 4.0)
        )
        val pick = AnalysisMath.pickBlunder(0.95, 20.0, cands, Stone.BLACK, 0.1)
        assertEquals(2, pick?.move?.x)
    }

    @Test
    fun blunderRespectsWhitePerspective() {
        // White to move: black-perspective root 4% / -22 means White
        // is at 96% / +22. The candidate at black-wr 0.14 is White
        // dropping to 86% — the obvious safe mistake.
        val cands = listOf(
            cand(0, 0.04, -22.0),
            cand(1, 0.14, -14.0),
            cand(2, 0.30, -6.0)
        )
        val pick = AnalysisMath.pickBlunder(0.04, -22.0, cands, Stone.WHITE, 0.0)
        assertEquals(1, pick?.move?.x)
    }

    @Test
    fun blunderRefusedWhenNotSafelyWinning() {
        val cands = listOf(cand(0, 0.85, 18.0), cand(1, 0.80, 12.0))
        // Root win rate 85% < 90%.
        assertNull(AnalysisMath.pickBlunder(0.85, 18.0, cands, Stone.BLACK, 0.0))
        // Root lead 12 < 15.
        val cands2 = listOf(cand(0, 0.95, 12.0), cand(1, 0.88, 10.5))
        assertNull(AnalysisMath.pickBlunder(0.95, 12.0, cands2, Stone.BLACK, 0.0))
    }

    @Test
    fun blunderRefusedWhenPositionUnsettled() {
        val cands = listOf(cand(0, 0.95, 20.0), cand(1, 0.85, 13.0))
        assertNull(AnalysisMath.pickBlunder(0.95, 20.0, cands, Stone.BLACK, 0.4))
    }

    @Test
    fun blunderRefusedWhenEveryMistakeTooCostly() {
        // Only alternative drops below the 80% floor.
        val cands = listOf(cand(0, 0.95, 20.0), cand(1, 0.75, 11.0))
        assertNull(AnalysisMath.pickBlunder(0.95, 20.0, cands, Stone.BLACK, 0.0))
        // Only alternative is a pass.
        val cands2 = listOf(cand(0, 0.95, 20.0), cand(1, 0.85, 13.0, pass = true))
        assertNull(AnalysisMath.pickBlunder(0.95, 20.0, cands2, Stone.BLACK, 0.0))
    }

    @Test
    fun scoreRangeBlackWin() {
        assertEquals(
            "比分范围：大概率黑胜 8～16 目",
            AnalysisMath.scoreRangeText(12.0, 4.0)
        )
    }

    @Test
    fun scoreRangeWhiteWin() {
        assertEquals(
            "比分范围：大概率白胜 8～16 目",
            AnalysisMath.scoreRangeText(-12.0, 4.0)
        )
    }

    @Test
    fun scoreRangeCrossingZero() {
        assertEquals(
            "比分范围：胜负未定，大概率在黑胜 7 目～白胜 3 目之间",
            AnalysisMath.scoreRangeText(2.0, 5.0)
        )
    }

    @Test
    fun scoreRangeKeepsDecimals() {
        // Mid-game shape: lead -8.8 with a wide 25.8 spread.
        assertEquals(
            "比分范围：胜负未定，大概率在黑胜 17 目～白胜 34.6 目之间",
            AnalysisMath.scoreRangeText(-8.8, 25.8)
        )
    }

    @Test
    fun scoreRangeTooTightIsNull() {
        assertNull(AnalysisMath.scoreRangeText(5.0, 0.3))
    }
}
