package com.tuxungo.android.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadPositionTest {

    @Test
    fun loadsValidPosition() {
        val g = GoGame(BoardSize(9, 9))
        val ok = g.loadPosition(
            listOf(Move(Stone.BLACK, 2, 2), Move(Stone.WHITE, 3, 3)),
            Stone.WHITE
        )
        assertTrue(ok)
        assertEquals(Stone.BLACK, g.stoneAt(2, 2))
        assertEquals(Stone.WHITE, g.stoneAt(3, 3))
        assertEquals(Stone.WHITE, g.current)
        assertTrue(g.moves.isEmpty())
    }

    @Test
    fun rejectsGroupWithoutLiberties() {
        val g = GoGame(BoardSize(9, 9))
        // White stone fully surrounded by black: no liberties.
        val stones = listOf(
            Move(Stone.WHITE, 1, 1),
            Move(Stone.BLACK, 0, 1), Move(Stone.BLACK, 2, 1),
            Move(Stone.BLACK, 1, 0), Move(Stone.BLACK, 1, 2)
        )
        assertFalse(g.loadPosition(stones, Stone.BLACK))
        // Game untouched.
        assertEquals(null, g.stoneAt(1, 1))
    }

    @Test
    fun rejectsOverlap() {
        val g = GoGame(BoardSize(9, 9))
        val stones = listOf(Move(Stone.BLACK, 4, 4), Move(Stone.WHITE, 4, 4))
        assertFalse(g.loadPosition(stones, Stone.BLACK))
    }
}
