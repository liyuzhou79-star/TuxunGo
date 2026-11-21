package com.tuxungo.android

import com.tuxungo.android.engine.MarkKind
import com.tuxungo.android.engine.ReviewMath
import com.tuxungo.android.engine.TurnStat
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewMathTest {

    private fun stat(
        turn: Int,
        wrBlack: Double,
        best: Move? = null,
        bestPass: Boolean = false,
        gap: Double = 0.0
    ) = TurnStat(turn, wrBlack, 0.0, best, bestPass, gap)

    @Test
    fun bigWinrateDropIsBlunderAndKey() {
        val moves = listOf(
            Move(Stone.BLACK, 3, 3),
            Move(Stone.WHITE, 4, 4)
        )
        val turns = listOf(
            stat(0, 0.50),
            stat(1, 0.50),
            stat(2, 0.75) // White to move was 50%, after White's move White is 25%
        )
        val marks = ReviewMath.marks(moves, turns)
        assertTrue(marks.any { it.moveNumber == 2 && it.kind == MarkKind.BLUNDER })
        assertTrue(marks.any { it.moveNumber == 2 && it.kind == MarkKind.KEY })
        val blunder = marks.first { it.kind == MarkKind.BLUNDER }
        assertEquals(-25.0, blunder.deltaPercent, 0.001)
    }

    @Test
    fun playingTopChoiceIsBrilliant() {
        val moves = listOf(Move(Stone.BLACK, 3, 3))
        val turns = listOf(
            stat(0, 0.50, best = Move(Stone.BLACK, 3, 3), gap = 0.12),
            stat(1, 0.55)
        )
        val marks = ReviewMath.marks(moves, turns)
        assertTrue(marks.any { it.moveNumber == 1 && it.kind == MarkKind.BRILLIANT })
    }

    @Test
    fun topChoiceWithTinyGapIsNotBrilliant() {
        // The obvious move in a quiet position: it matches the top
        // choice, but the alternatives were just as good, so calling
        // it brilliant would flood the list with noise.
        val moves = listOf(Move(Stone.BLACK, 3, 3))
        val turns = listOf(
            stat(0, 0.50, best = Move(Stone.BLACK, 3, 3), gap = 0.01),
            stat(1, 0.50)
        )
        val marks = ReviewMath.marks(moves, turns)
        assertTrue(marks.none { it.kind == MarkKind.BRILLIANT })
    }

    @Test
    fun quietGameHasOnlyKeyMove() {
        val moves = listOf(Move(Stone.BLACK, 3, 3), Move(Stone.WHITE, 4, 4))
        val turns = listOf(stat(0, 0.50), stat(1, 0.52), stat(2, 0.51))
        val marks = ReviewMath.marks(moves, turns)
        assertEquals(1, marks.size)
        assertEquals(MarkKind.KEY, marks[0].kind)
    }

    @Test
    fun turnsRoundTrip() {
        val turns = listOf(
            TurnStat(0, 0.5, 1.25, Move(Stone.BLACK, 3, 3), false, bestGap = 0.083),
            TurnStat(1, 0.623, -2.5, null, true)
        )
        val back = ReviewMath.decodeTurns(ReviewMath.encodeTurns(turns))
        assertEquals(2, back.size)
        assertEquals(0.083, back[0].bestGap, 0.001)
        assertEquals(0.623, back[1].winrateBlack, 0.001)
        assertEquals(-2.5, back[1].scoreLeadBlack, 0.001)
        assertEquals(3, back[0].bestMove?.x)
        assertTrue(back[1].bestIsPass)
    }

    @Test
    fun pvRoundTripAndParse() {
        val turns = listOf(
            TurnStat(0, 0.5, 0.0, Move(Stone.BLACK, 3, 3), false, 0.0, "Q16 R17 D4")
        )
        val back = ReviewMath.decodeTurns(ReviewMath.encodeTurns(turns))
        assertEquals("Q16 R17 D4", back[0].bestPv)
        val sparse = ReviewMath.decodeSparse(
            ReviewMath.encodeSparse(mapOf(0 to turns[0]))
        )
        assertEquals("Q16 R17 D4", sparse[0]!!.bestPv)
        // Old caches without the PV field still decode.
        assertEquals("", ReviewMath.decodeTurns("0.500,0.000,3,3,0.000")[0].bestPv)

        val pv = ReviewMath.parsePv(
            "Q16 R17 pass D4", BoardSize(19, 19), Stone.BLACK
        )
        assertEquals(4, pv.size)
        assertEquals(Stone.BLACK, pv[0].stone)
        assertEquals(15, pv[0].x)
        assertEquals(3, pv[0].y)
        assertEquals(Stone.WHITE, pv[1].stone)
        assertTrue(pv[2].isPass)
        assertEquals(Stone.WHITE, pv[3].stone)
        assertTrue(ReviewMath.parsePv("", BoardSize(19, 19), Stone.BLACK).isEmpty())
    }

    @Test
    fun sparseStatsRoundTrip() {
        val stats = mapOf(
            0 to TurnStat(0, 0.5, 0.0, Move(Stone.BLACK, 3, 3), false),
            2 to TurnStat(2, 0.61, 2.5, null, true),
            5 to TurnStat(5, 0.42, -1.25, Move(Stone.WHITE, 10, 10), false)
        )
        val back = ReviewMath.decodeSparse(ReviewMath.encodeSparse(stats))
        assertEquals(3, back.size)
        assertEquals(0.61, back[2]!!.winrateBlack, 0.001)
        assertEquals(-1.25, back[5]!!.scoreLeadBlack, 0.001)
        assertEquals(10, back[5]!!.bestMove?.x)
        assertTrue(back[2]!!.bestIsPass)
        assertTrue(ReviewMath.decodeSparse(null).isEmpty())
        assertTrue(ReviewMath.decodeSparse("").isEmpty())
    }

    @Test
    fun libraryMoveEncodingRoundTrip() {
        val moves = listOf(
            Move(Stone.BLACK, 3, 4),
            Move.pass(Stone.WHITE),
            Move(Stone.BLACK, 0, 18)
        )
        val back = LibraryStore.decodeMoves(LibraryStore.encodeMoves(moves))
        assertEquals(moves, back)
    }

    @Test
    fun marksRefusesSparseTurns() {
        // A live game's recorded stats are sparse (only the turns
        // that got analyzed, no turn-0 entry). marks() indexes turns
        // by move number, so it must refuse a non-dense list instead
        // of crashing the caller (the in-game trend dialog did).
        val moves = listOf(
            Move(Stone.BLACK, 3, 3),
            Move(Stone.WHITE, 4, 4),
            Move(Stone.BLACK, 5, 5)
        )
        val sparse = listOf(stat(1, 0.6), stat(3, 0.4))
        assertTrue(ReviewMath.marks(moves, sparse).isEmpty())
    }
}
