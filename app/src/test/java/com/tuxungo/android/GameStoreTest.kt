package com.tuxungo.android

import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStoreTest {

    @Test
    fun movesRoundTrip() {
        val moves = listOf(
            Move(Stone.BLACK, 3, 3),
            Move(Stone.WHITE, 15, 15),
            Move.pass(Stone.BLACK),
            Move(Stone.WHITE, 0, 24)
        )
        val decoded = GameStore.decodeMoves(GameStore.encodeMoves(moves))
        assertEquals(moves, decoded)
    }

    @Test
    fun emptyRoundTrip() {
        assertEquals("", GameStore.encodeMoves(emptyList()))
        assertEquals(emptyList<Move>(), GameStore.decodeMoves(""))
    }
}
