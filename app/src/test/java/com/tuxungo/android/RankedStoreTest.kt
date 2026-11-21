package com.tuxungo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ranked tallies persist as "rank=wins-losses" pairs joined by
 * ";". Parsing must be tolerant: a corrupted segment is skipped or
 * zeroed, never fatal, so a bad write can never wipe the career page.
 */
class RankedStoreTest {

    @Test
    fun emptyIsEmpty() {
        assertTrue(RankedStore.parse("").isEmpty())
    }

    @Test
    fun parsesPairs() {
        val map = RankedStore.parse("9d=3-2;10k=0-5;18k=12-0")
        assertEquals(3, map.size)
        assertEquals(3, map["9d"]!![0])
        assertEquals(2, map["9d"]!![1])
        assertEquals(0, map["10k"]!![0])
        assertEquals(5, map["10k"]!![1])
        assertEquals(12, map["18k"]!![0])
    }

    @Test
    fun malformedSegmentsSkipped() {
        val map = RankedStore.parse("junk;5k=1-1;noequals;3d=2")
        assertEquals(1, map.size)
        assertEquals(1, map["5k"]!![0])
    }

    @Test
    fun nonNumericCountsBecomeZero() {
        val map = RankedStore.parse("5k=x-y")
        assertEquals(0, map["5k"]!![0])
        assertEquals(0, map["5k"]!![1])
    }
}
