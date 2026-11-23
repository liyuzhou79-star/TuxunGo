package com.tuxungo.android.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rectangular boards (5–25 per side) must play by the same rules. */
class RectBoardTest {

    @Test
    fun cornerCaptureOnARectangularBoard() {
        val g = GoGame(BoardSize(5, 7), 7.5)
        assertTrue(g.play(1, 0)) // Black
        assertTrue(g.play(0, 0)) // White into the corner
        assertTrue(g.play(0, 1)) // Black fills the last liberty
        assertNull(g.stoneAt(0, 0))
        assertEquals(1, g.capturesByBlack)
        assertEquals(3, g.moves.size)
    }

    @Test
    fun passAlternatesOnARectangularBoard() {
        val g = GoGame(BoardSize(13, 9), 6.5)
        g.pass()
        assertEquals(Stone.WHITE, g.current)
        g.pass()
        assertEquals(Stone.BLACK, g.current)
        assertEquals(2, g.moves.size)
    }
}
