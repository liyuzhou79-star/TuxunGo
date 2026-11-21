package com.tuxungo.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ranked-AI ladder: RANKS order drives the setup dropdown, the
 * in-game card suffix and the stats page's "strongest rank beaten"
 * pick, so its shape and labels are pinned down here.
 */
class SideAiStoreTest {

    @Test
    fun ladderShape() {
        val ranks = SideAiStore.RANKS
        assertEquals(28, ranks.size) // max + 9 dan + 18 kyu
        assertEquals("max", ranks.first())
        assertEquals("9d", ranks[1])
        assertEquals("1d", ranks[9])
        assertEquals("1k", ranks[10])
        assertEquals("18k", ranks.last())
        assertEquals(ranks.size, ranks.toSet().size)
    }

    @Test
    fun ladderIsStrongestFirst() {
        val ranks = SideAiStore.RANKS
        assertTrue(ranks.indexOf("9d") < ranks.indexOf("1d"))
        assertTrue(ranks.indexOf("1d") < ranks.indexOf("1k"))
        assertTrue(ranks.indexOf("1k") < ranks.indexOf("18k"))
    }

    @Test
    fun labels() {
        assertEquals("Max（最强）", SideAiStore.rankLabel("max"))
        assertEquals("9 段", SideAiStore.rankLabel("9d"))
        assertEquals("1 段", SideAiStore.rankLabel("1d"))
        assertEquals("5 级", SideAiStore.rankLabel("5k"))
        assertEquals("18 级", SideAiStore.rankLabel("18k"))
    }

    @Test
    fun profiles() {
        assertNull(SideAiStore.rankProfile("max"))
        assertEquals("rank_9d", SideAiStore.rankProfile("9d"))
        assertEquals("rank_10k", SideAiStore.rankProfile("10k"))
    }
}
