package com.tuxungo.android.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SGF import: parsing, setup handling, replay validation. */
class SgfImportTest {

    @Test
    fun simpleGameParses() {
        val games = SgfImport.parseAll(
            "(;GM[1]FF[4]SZ[19]KM[7.5]PB[Alice]PW[Bob]RE[B+7.5]" +
                ";B[pd];W[dd];B[pp])"
        )
        assertEquals(1, games.size)
        val g = games[0]
        assertEquals(19, g.size.width)
        assertEquals(19, g.size.height)
        assertEquals(7.5, g.komi, 0.001)
        assertEquals("Alice vs Bob", g.title)
        assertEquals("黑方胜 7.5 子", g.resultText)
        assertEquals(Stone.BLACK, g.startPlayer)
        assertEquals(3, g.moves.size)
        assertEquals(Move(Stone.BLACK, 15, 3), g.moves[0])
        assertEquals(Move(Stone.WHITE, 3, 3), g.moves[1])
        assertEquals(Move(Stone.BLACK, 15, 15), g.moves[2])
        assertNull(g.truncatedAt)
    }

    @Test
    fun passesParse() {
        val g = SgfImport.parseAll("(;SZ[19];B[pd];W[];B[tt])")[0]
        assertEquals(3, g.moves.size)
        assertTrue(g.moves[1].isPass)
        assertTrue(g.moves[2].isPass)
        assertNull(g.truncatedAt)
    }

    @Test
    fun handicapSetup() {
        val g = SgfImport.parseAll("(;SZ[19]HA[2]AB[pd][dp];W[dd])")[0]
        assertEquals(2, g.handicap)
        assertEquals(2, g.setupStones.size)
        assertTrue(g.setupStones.all { it.stone == Stone.BLACK })
        assertEquals(Stone.WHITE, g.startPlayer)
        assertEquals(1, g.moves.size)
        assertNull(g.truncatedAt)
    }

    @Test
    fun rectangularBoard() {
        val g = SgfImport.parseAll("(;SZ[13:9];B[aa];W[bc])")[0]
        assertEquals(13, g.size.width)
        assertEquals(9, g.size.height)
        assertEquals(Move(Stone.WHITE, 1, 2), g.moves[1])
    }

    @Test
    fun variationTakesMainLine() {
        val g = SgfImport.parseAll(
            "(;SZ[19];B[pd](;W[dd];B[pp])(;W[pp]))"
        )[0]
        assertEquals(3, g.moves.size)
        assertEquals(Move(Stone.WHITE, 3, 3), g.moves[1])
        assertEquals(Move(Stone.BLACK, 15, 15), g.moves[2])
    }

    @Test
    fun multiGameFile() {
        val games = SgfImport.parseAll("(;SZ[19];B[pd])(;SZ[9];B[ee])")
        assertEquals(2, games.size)
        assertEquals(9, games[1].size.width)
        assertEquals(Move(Stone.BLACK, 4, 4), games[1].moves[0])
    }

    @Test
    fun setupPositionWithPlayer() {
        val g = SgfImport.parseAll("(;SZ[19]AB[dd]AW[pp]PL[W];W[pd])")[0]
        assertEquals(2, g.setupStones.size)
        assertEquals(Stone.WHITE, g.startPlayer)
        assertEquals(1, g.moves.size)
        assertNull(g.truncatedAt)
    }

    @Test
    fun illegalMoveTruncates() {
        val g = SgfImport.parseAll("(;SZ[19];B[pd];W[pd])")[0]
        assertEquals(1, g.moves.size)
        assertEquals(2, g.truncatedAt)
    }

    @Test
    fun oversizedBoardRejected() {
        assertTrue(SgfImport.parseAll("(;SZ[30];B[aa])").isEmpty())
        assertTrue(SgfImport.parseAll("not an sgf").isEmpty())
        assertTrue(SgfImport.parseAll("").isEmpty())
    }

    @Test
    fun friendlyResults() {
        assertEquals("白方胜（认输）", SgfImport.friendlyResult("W+R"))
        assertEquals("黑方胜（超时）", SgfImport.friendlyResult("B+T"))
        assertEquals("黑方胜 3.5 子", SgfImport.friendlyResult("B+3.5"))
        assertEquals("和棋", SgfImport.friendlyResult("0"))
        assertEquals("", SgfImport.friendlyResult(""))
    }
}
