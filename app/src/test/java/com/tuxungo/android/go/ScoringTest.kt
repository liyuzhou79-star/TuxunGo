package com.tuxungo.android.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {

    /**
     * 9x9 corner: a black wall (2,0),(2,1),(2,2),(1,2),(0,2) encloses
     * the corner points (0,0),(1,0),(0,1),(1,1). A white stone sits at
     * (0,0) inside — alive it contests the corner; dead, the whole
     * corner is Black's territory.
     */
    private fun cornerGame(): GoGame {
        val g = GoGame(BoardSize(9, 9), 7.5)
        val ok = g.loadPosition(
            listOf(
                Move(Stone.WHITE, 0, 0),
                Move(Stone.BLACK, 2, 0), Move(Stone.BLACK, 2, 1),
                Move(Stone.BLACK, 2, 2), Move(Stone.BLACK, 1, 2),
                Move(Stone.BLACK, 0, 2)
            ),
            Stone.BLACK
        )
        assertTrue(ok)
        return g
    }

    @Test
    fun groupAtFindsWholeGroup() {
        val g = cornerGame()
        assertEquals(5, g.groupAt(2, 0).size)
        assertEquals(1, g.groupAt(0, 0).size)
        assertTrue(g.groupAt(5, 5).isEmpty())
    }

    @Test
    fun deadStonePointGoesToOpponent() {
        val g = cornerGame()
        val alive = g.score()
        // Alive: corner empties border both colors -> neutral.
        assertEquals(5, alive.blackStones)
        assertEquals(1, alive.whiteStones)
        assertEquals(0, alive.blackTerritory)
        assertEquals(0, alive.whiteTerritory)

        val withDead = g.scoreWithDead(setOf(g.index(0, 0)))
        // Dead: the white stone vanishes and all 4 corner points
        // (its point + 3 empties) become Black territory.
        assertEquals(0, withDead.whiteStones)
        assertEquals(4, withDead.blackTerritory)
        assertEquals(5, withDead.blackStones)
    }
}
