package com.tuxungo.android.ui

import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnalysisCacheKeyTest {

    private fun key(
        moves: List<Move>,
        modelId: String = "bundled",
        includeHuman: Boolean = false,
        base: List<Move> = emptyList(),
        komi: Double = 7.5
    ): String = analysisCacheKey(19, 19, komi, modelId, includeHuman, base, moves)

    private val opening = listOf(
        Move(Stone.BLACK, 3, 3),
        Move(Stone.WHITE, 15, 15),
        Move(Stone.BLACK, 15, 3)
    )

    @Test
    fun samePositionSameKey() {
        assertEquals(key(opening), key(opening.toList()))
    }

    @Test
    fun differentMoveDifferentKey() {
        val other = opening.dropLast(1) + Move(Stone.BLACK, 3, 15)
        assertNotEquals(key(opening), key(other))
    }

    @Test
    fun passIsDistinctFromARealMove() {
        val withPass = opening + Move.pass(Stone.WHITE)
        val withMove = opening + Move(Stone.WHITE, 0, 0)
        assertNotEquals(key(withPass), key(withMove))
    }

    @Test
    fun modelAndHumanFlagMatter() {
        assertNotEquals(key(opening), key(opening, modelId = "strong"))
        assertNotEquals(key(opening), key(opening, includeHuman = true))
    }

    @Test
    fun basePositionAndKomiMatter() {
        val base = listOf(Move(Stone.BLACK, 9, 9))
        assertNotEquals(key(opening), key(opening, base = base))
        assertNotEquals(key(opening), key(opening, komi = 6.5))
    }
}
