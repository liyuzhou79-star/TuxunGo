package com.tuxungo.android.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoGameTest {

    private fun game(w: Int = 9, h: Int = 9, komi: Double = 7.5) = GoGame(BoardSize(w, h), komi)

    @Test
    fun playersAlternateAndStonesArePlaced() {
        val g = game()
        assertTrue(g.play(4, 4))
        assertEquals(Stone.WHITE, g.current)
        assertEquals(Stone.BLACK, g.stoneAt(4, 4))
        assertTrue(g.play(0, 0))
        assertEquals(Stone.WHITE, g.stoneAt(0, 0))
        assertEquals(2, g.moves.size)
    }

    @Test
    fun occupiedPointIsIllegal() {
        val g = game()
        assertTrue(g.play(3, 3))
        assertFalse(g.play(3, 3))
    }

    @Test
    fun capturesSingleStone() {
        val g = game()
        // White stone at (1,1) surrounded by Black.
        assertTrue(g.play(1, 0)) // B
        assertTrue(g.play(1, 1)) // W
        assertTrue(g.play(0, 1)) // B
        assertTrue(g.play(8, 8)) // W elsewhere
        assertTrue(g.play(2, 1)) // B
        assertTrue(g.play(8, 7)) // W elsewhere
        assertTrue(g.play(1, 2)) // B captures
        assertNull(g.stoneAt(1, 1))
        assertEquals(1, g.capturesByBlack)
    }

    @Test
    fun suicideIsIllegal() {
        val g = game()
        assertTrue(g.play(1, 0)) // B
        assertTrue(g.play(8, 8)) // W
        assertTrue(g.play(0, 1)) // B
        assertTrue(g.play(8, 7)) // W
        assertTrue(g.play(2, 1)) // B
        assertTrue(g.play(8, 6)) // W
        assertTrue(g.play(1, 2)) // B completes the ring around (1,1)
        // Now White to move cannot play into (1,1): suicide.
        assertFalse(g.play(1, 1))
    }

    @Test
    fun koForbidsImmediateRecapture() {
        val g = game()
        // Classic ko shape:
        //   . B W .
        //   B W . W  -> simplified small fight on 9x9
        // Build: Black (1,0),(0,1),(1,2),(2,1 as capturing stone), White (2,0),(3,1),(2,2),(1,1)
        assertTrue(g.play(1, 0)) // B
        assertTrue(g.play(2, 0)) // W
        assertTrue(g.play(0, 1)) // B
        assertTrue(g.play(3, 1)) // W
        assertTrue(g.play(1, 2)) // B
        assertTrue(g.play(2, 2)) // W
        assertTrue(g.play(8, 8)) // B filler
        assertTrue(g.play(1, 1)) // W inside
        assertTrue(g.play(2, 1)) // B captures W(1,1)
        assertNull(g.stoneAt(1, 1))
        assertEquals(1, g.capturesByBlack)
        // White immediately recapturing at (1,1) would restore the previous
        // position, which superko forbids.
        assertFalse(g.play(1, 1))
        // After an intervening move elsewhere, the recapture becomes legal.
        assertTrue(g.play(8, 0)) // W elsewhere
        assertTrue(g.play(8, 1)) // B elsewhere
        assertTrue(g.play(1, 1)) // W recaptures
        assertEquals(1, g.capturesByWhite)
    }

    @Test
    fun twoPassesDoNotFinishTheGame() {
        // 2.64.0: the user asked for the both-pass auto-finish to
        // be removed — a pass is just a pass, the game ends only
        // via scoring, resign, timeout or the referee.
        val g = game()
        assertTrue(g.play(4, 4))
        assertTrue(g.pass())
        assertFalse(g.finished)
        assertTrue(g.pass())
        assertFalse(g.finished)
        assertTrue(g.play(0, 0))
    }

    @Test
    fun undoRestoresCaptures() {
        val g = game()
        assertTrue(g.play(1, 0))
        assertTrue(g.play(1, 1))
        assertTrue(g.play(0, 1))
        assertTrue(g.play(8, 8))
        assertTrue(g.play(2, 1))
        assertTrue(g.play(8, 7))
        assertTrue(g.play(1, 2))
        assertEquals(1, g.capturesByBlack)
        assertTrue(g.undo())
        assertEquals(0, g.capturesByBlack)
        assertEquals(Stone.WHITE, g.stoneAt(1, 1))
        assertEquals(Stone.BLACK, g.current)
    }

    @Test
    fun scoringCountsTerritory() {
        // Tiny 5x5: Black wall across the middle row splits the board;
        // the top rows border only Black? They also touch the edge only.
        val g = game(5, 5, komi = 0.5)
        // Black fills row y=2.
        for (x in 0..4) {
            assertTrue(g.play(x, 2))
            if (x < 4) assertTrue(g.pass()) // White passes to keep alternation simple
        }
        val s = g.score()
        assertEquals(5, s.blackStones)
        // Rows 0-1 (10 points) border only Black; rows 3-4 also border only Black.
        assertEquals(20, s.blackTerritory)
        assertEquals(0, s.whiteTerritory)
    }

    @Test
    fun handicapSetupOn19() {
        val g = GoGame(BoardSize(19, 19))
        assertTrue(g.setupHandicap(4))
        assertEquals(4, g.handicapStones.size)
        assertEquals(Stone.WHITE, g.current)
        assertEquals(Stone.BLACK, g.stoneAt(3, 3))
        assertEquals(Stone.BLACK, g.stoneAt(15, 15))
    }

    @Test
    fun rectangularBoardWorks() {
        val g = game(13, 9)
        assertTrue(g.play(12, 8))
        assertFalse(g.play(13, 8))
        assertEquals(Stone.BLACK, g.stoneAt(12, 8))
    }

    @Test
    fun sgfExportContainsMovesAndSize() {
        val g = game(13, 9)
        assertTrue(g.play(3, 3))
        assertTrue(g.pass())
        val sgf = Sgf.export(g)
        assertTrue(sgf.contains("SZ[13:9]"))
        assertTrue(sgf.contains(";B[dd]"))
        assertTrue(sgf.contains(";W[tt]"))
    }
}
