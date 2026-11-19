package com.tuxungo.android.engine

import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KataEngineVertexTest {

    @Test
    fun cornersConvertBothWays() {
        val size = BoardSize(19, 19)
        // Our (0,0) is top-left; GTP calls it A19.
        assertEquals("A19", KataEngine.toVertex(Move(Stone.BLACK, 0, 0), size))
        assertEquals("T1", KataEngine.toVertex(Move(Stone.BLACK, 18, 18), size))
        assertEquals(Move(Stone.WHITE, 0, 0), KataEngine.fromVertex("A19", Stone.WHITE, size))
        assertEquals(Move(Stone.WHITE, 18, 18), KataEngine.fromVertex("T1", Stone.WHITE, size))
    }

    @Test
    fun letterISkipped() {
        val size = BoardSize(19, 19)
        // x=8 is the 9th column; GTP skips I, so it is J.
        assertEquals("J10", KataEngine.toVertex(Move(Stone.BLACK, 8, 9), size))
        assertEquals(Move(Stone.BLACK, 8, 9), KataEngine.fromVertex("J10", Stone.BLACK, size))
    }

    @Test
    fun passAndRectangularBoard() {
        val size = BoardSize(13, 9)
        assertEquals("pass", KataEngine.toVertex(Move.pass(Stone.BLACK), size))
        assertNull(KataEngine.fromVertex("pass", Stone.BLACK, size))
        assertEquals("M9", KataEngine.toVertex(Move(Stone.BLACK, 12, 0), size))
        assertEquals("A1", KataEngine.toVertex(Move(Stone.BLACK, 0, 8), size))
    }
}
