package com.tuxungo.android.engine

import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnStatCodecTest {

    private fun stat(
        turn: Int,
        wr: Double = 0.5,
        lead: Double = 0.0,
        best: Move? = null,
        gap: Double = 0.0,
        pv: String = "",
        deliberate: Boolean = false
    ) = TurnStat(
        turn = turn,
        winrateBlack = wr,
        scoreLeadBlack = lead,
        bestMove = best,
        bestIsPass = best == null,
        bestGap = gap,
        bestPv = pv,
        deliberate = deliberate
    )

    @Test
    fun denseRoundTripKeepsEverything() {
        val turns = listOf(
            stat(0),
            stat(1, wr = 0.623, lead = -3.25, best = Move(Stone.BLACK, 3, 15),
                gap = 0.125, pv = "Q16 R17"),
            stat(2, wr = 0.91, lead = 18.5, deliberate = true)
        )
        val back = ReviewMath.decodeTurns(ReviewMath.encodeTurns(turns))
        assertEquals(3, back.size)
        assertEquals(0.623, back[1].winrateBlack, 1e-9)
        assertEquals(-3.25, back[1].scoreLeadBlack, 1e-9)
        assertEquals(3, back[1].bestMove!!.x)
        assertEquals(15, back[1].bestMove!!.y)
        assertEquals(0.125, back[1].bestGap, 1e-9)
        assertEquals("Q16 R17", back[1].bestPv)
        assertFalse(back[1].deliberate)
        assertTrue(back[2].deliberate)
        assertTrue(back[2].bestIsPass)
        assertNull(back[2].bestMove)
    }

    @Test
    fun legacySixFieldRowsDecodeWithoutDeliberate() {
        // Rows written before the deliberate flag existed have no
        // 7th field and must read as not-deliberate.
        val back = ReviewMath.decodeTurns("0.500,3.250,4,4,0.125,Q16")
        assertEquals(1, back.size)
        assertFalse(back[0].deliberate)
        assertEquals(4, back[0].bestMove!!.x)
        assertEquals(3.25, back[0].scoreLeadBlack, 1e-9)
    }

    @Test
    fun sparseRoundTripKeepsTurnNumbersAndDeliberate() {
        val stats = mapOf(
            0 to stat(0),
            5 to stat(5, wr = 0.87, lead = 12.0, deliberate = true),
            9 to stat(9, wr = 0.4, lead = -6.5, best = Move(Stone.WHITE, 0, 0))
        )
        val back = ReviewMath.decodeSparse(ReviewMath.encodeSparse(stats))
        assertEquals(setOf(0, 5, 9), back.keys)
        assertTrue(back[5]!!.deliberate)
        assertEquals(0.87, back[5]!!.winrateBlack, 1e-9)
        assertEquals(0, back[9]!!.bestMove!!.x)
        assertFalse(back[0]!!.deliberate)
    }

    @Test
    fun blankAndNullDecodeToEmpty() {
        assertTrue(ReviewMath.decodeTurns("").isEmpty())
        assertTrue(ReviewMath.decodeSparse(null).isEmpty())
        assertTrue(ReviewMath.decodeSparse("").isEmpty())
    }
}
