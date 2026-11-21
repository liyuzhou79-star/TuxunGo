package com.tuxungo.android.go

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SGF export shape: header fields, move encoding, pass, handicap,
 * custom setup stones, comments and the result line. The AP version
 * string changes every release, so it is matched by prefix only.
 */
class SgfTest {

    @Test
    fun headerAndFirstMoves() {
        val g = GoGame(BoardSize(19, 19), 7.5)
        assertTrue(g.play(15, 3)) // Black Q16 -> pd
        assertTrue(g.pass()) // White passes -> tt
        val sgf = Sgf.export(g)
        assertTrue(sgf.startsWith("(;GM[1]FF[4]CA[UTF-8]AP[TuxunGo:"))
        assertTrue(sgf.contains("SZ[19]"))
        assertTrue(sgf.contains("KM[7.5]"))
        assertTrue(sgf.contains(";B[pd]"))
        assertTrue(sgf.contains(";W[tt]"))
        assertTrue(sgf.endsWith(")"))
    }

    @Test
    fun rectangularBoardSize() {
        val g = GoGame(BoardSize(13, 9), 6.5)
        val sgf = Sgf.export(g)
        assertTrue(sgf.contains("SZ[13:9]"))
        assertTrue(sgf.contains("KM[6.5]"))
    }

    @Test
    fun handicapStonesExported() {
        val g = GoGame(BoardSize(19, 19), 0.5)
        assertTrue(g.setupHandicap(2))
        val sgf = Sgf.export(g)
        assertTrue(sgf.contains("HA[2]"))
        assertTrue(sgf.contains("AB"))
        // Standard two-stone points: (15,3)=pd and (3,15)=dp.
        assertTrue(sgf.contains("[pd]"))
        assertTrue(sgf.contains("[dp]"))
    }

    @Test
    fun resignResult() {
        val g = GoGame(BoardSize(19, 19), 7.5)
        assertTrue(g.play(3, 3))
        g.resign(Stone.BLACK)
        val sgf = Sgf.export(g)
        assertTrue(sgf.contains("RE[W+R]"))
    }

    @Test
    fun passFinishedGameScoresKomi() {
        val g = GoGame(BoardSize(19, 19), 7.5)
        assertTrue(g.pass())
        assertTrue(g.pass())
        assertTrue(g.finished)
        val sgf = Sgf.export(g)
        // Empty board: White wins by the 7.5 komi.
        assertTrue(sgf.contains("RE[W+7.5]"))
    }

    @Test
    fun setupStonesAndStartPlayer() {
        val g = GoGame(BoardSize(9, 9), 7.5)
        val setup = listOf(
            Move(Stone.BLACK, 2, 2),
            Move(Stone.WHITE, 6, 6)
        )
        val sgf = Sgf.export(
            g, setupStones = setup, startPlayer = Stone.WHITE
        )
        assertTrue(sgf.contains("AB[cc]"))
        assertTrue(sgf.contains("AW[gg]"))
        assertTrue(sgf.contains("PL[W]"))
    }

    @Test
    fun commentEscaping() {
        val g = GoGame(BoardSize(19, 19), 7.5)
        assertTrue(g.play(3, 3))
        val sgf = Sgf.export(g, comments = mapOf(1 to "好棋]含括号"))
        assertTrue(sgf.contains("C[好棋\\]含括号]"))
    }
}
