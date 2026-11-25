package com.tuxungo.android.engine

import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 「绝 / 定 / 销」 detectors: ko resolution is pure board
 * rules; the global lock needs a calm high win rate plus the
 * pessimistic bound; a dragon flip needs ownership to actually
 * change state and persist.
 */
class DecisiveMathTest {

    private fun board(
        size: BoardSize,
        vararg stones: Triple<Int, Int, Stone>
    ): Array<Stone?> {
        val b = arrayOfNulls<Stone>(size.area)
        for ((x, y, s) in stones) b[y * size.width + x] = s
        return b
    }

    private fun moves(vararg spec: Pair<Stone, Pair<Int, Int>>): List<Move> =
        spec.map { (s, p) -> Move(s, p.first, p.second) }

    // The classic ko diagram on 5x5: the white stone at (2,1) is
    // in atari, its only liberty is (1,1); the surrounding stones
    // make the recapture land in atari again.
    private fun koBoards(size: BoardSize): List<Array<Stone?>> {
        val surround = arrayOf(
            Triple(0, 1, Stone.WHITE), Triple(1, 0, Stone.WHITE),
            Triple(1, 2, Stone.WHITE),
            Triple(2, 0, Stone.BLACK), Triple(2, 2, Stone.BLACK),
            Triple(3, 1, Stone.BLACK)
        )
        val before = board(size, *surround, Triple(2, 1, Stone.WHITE))
        val afterCapture = board(size, *surround, Triple(1, 1, Stone.BLACK))
        val afterThreat = board(
            size, *surround, Triple(1, 1, Stone.BLACK),
            Triple(4, 4, Stone.WHITE)
        )
        val afterFill = board(
            size, *surround, Triple(1, 1, Stone.BLACK),
            Triple(4, 4, Stone.WHITE), Triple(2, 1, Stone.BLACK)
        )
        return listOf(before, afterCapture, afterThreat, afterFill)
    }

    @Test
    fun koFillEarnsXiao() {
        val size = BoardSize(5, 5)
        val boards = koBoards(size)
        val mv = moves(
            Stone.BLACK to (1 to 1), // captures the white stone, ko opens
            Stone.WHITE to (4 to 4), // ko threat elsewhere
            Stone.BLACK to (2 to 1)  // fills the ko: resolved
        )
        val marks = DecisiveMath.detect(
            size, mv, emptyMap(), emptyMap(), boards, finalized = true
        )
        assertEquals(1, marks.size)
        assertEquals(3, marks[0].moveNumber)
        assertEquals(DecisiveKind.KO, marks[0].kind)
        assertEquals(DecisiveReason.KO_RESOLVED, marks[0].reason)
    }

    @Test
    fun koRecaptureIsNotXiao() {
        val size = BoardSize(5, 5)
        val surround = arrayOf(
            Triple(0, 1, Stone.WHITE), Triple(1, 0, Stone.WHITE),
            Triple(1, 2, Stone.WHITE),
            Triple(2, 0, Stone.BLACK), Triple(2, 2, Stone.BLACK),
            Triple(3, 1, Stone.BLACK)
        )
        val before = board(size, *surround, Triple(2, 1, Stone.WHITE))
        val afterCapture = board(size, *surround, Triple(1, 1, Stone.BLACK))
        val afterThreat = board(
            size, *surround, Triple(1, 1, Stone.BLACK),
            Triple(4, 4, Stone.WHITE)
        )
        // White recaptures at (2,1): the ko flips, nothing resolved.
        val afterRecapture = board(
            size, *surround, Triple(4, 4, Stone.WHITE),
            Triple(2, 1, Stone.WHITE)
        )
        val mv = moves(
            Stone.BLACK to (1 to 1),
            Stone.WHITE to (4 to 4),
            Stone.WHITE to (2 to 1)
        )
        val marks = DecisiveMath.detect(
            size, mv, emptyMap(), emptyMap(),
            listOf(before, afterCapture, afterThreat, afterRecapture),
            finalized = true
        )
        assertTrue(marks.none { it.kind == DecisiveKind.KO })
    }

    @Test
    fun koShapeDetectedBothWays() {
        val size = BoardSize(5, 5)
        val boards = koBoards(size)
        val first = DecisiveMath.koPoints(boards[0], size)
        assertEquals(Stone.WHITE, first[1 * size.width + 1])
        val second = DecisiveMath.koPoints(boards[1], size)
        assertEquals(Stone.BLACK, second[1 * size.width + 2])
    }

    private fun stat(turn: Int, wr: Double, lead: Double) =
        TurnStat(turn, wr, lead, null, false)

    @Test
    fun globalLockNeedsCalmWindowAndBound() {
        val size = BoardSize(19, 19)
        // 39 empty points left; a 40-point lead beats them all.
        val filled = arrayOfNulls<Stone>(size.area)
        for (i in 0 until size.area - 39) filled[i] = Stone.BLACK
        val boards = List(31) { filled }
        val mv = (1..30).map { n ->
            Move(if (n % 2 == 1) Stone.BLACK else Stone.WHITE, 0, 0)
        }
        val stats = HashMap<Int, TurnStat>()
        for (t in 0..30) {
            val wr = if (t >= 20) 0.96 else 0.55
            stats[t] = stat(t, wr, if (t >= 20) 40.0 else 5.0)
        }
        val marks = DecisiveMath.detect(
            size, mv, stats, emptyMap(), boards, finalized = false
        )
        val lock = marks.firstOrNull { it.reason == DecisiveReason.LOCK }
        assertTrue("expected a LOCK mark, got $marks", lock != null)
        assertEquals(25, lock!!.moveNumber)

        // Same shape but the lead no longer beats every empty point.
        val weak = HashMap<Int, TurnStat>()
        for (t in 0..30) {
            val wr = if (t >= 20) 0.96 else 0.55
            weak[t] = stat(t, wr, if (t >= 20) 30.0 else 5.0)
        }
        val marks2 = DecisiveMath.detect(
            size, mv, weak, emptyMap(), boards, finalized = false
        )
        assertTrue(marks2.none { it.reason == DecisiveReason.LOCK })
    }

    @Test
    fun comebackNeedsDeepHoleAndSteadyClimb() {
        val size = BoardSize(19, 19)
        val boards = List(41) { arrayOfNulls<Stone>(size.area) }
        val mv = (1..40).map { n ->
            Move(if (n % 2 == 1) Stone.BLACK else Stone.WHITE, 0, 0)
        }
        val wr = DoubleArray(41) { t ->
            when {
                t <= 5 -> 0.25
                t <= 20 -> 0.35 + (t - 6) * 0.035 // steady climb
                t <= 28 -> 0.90
                t == 29 -> 0.90
                else -> 0.93
            }
        }
        val stats = HashMap<Int, TurnStat>()
        for (t in 0..40) {
            val lead = when {
                t == 28 -> 10.0
                t == 29 -> 13.0
                t >= 30 -> 14.0
                else -> 5.0
            }
            stats[t] = stat(t, wr[t], lead)
        }
        val marks = DecisiveMath.detect(
            size, mv, stats, emptyMap(), boards, finalized = true
        )
        val cb = marks.firstOrNull { it.reason == DecisiveReason.COMEBACK }
        assertTrue("expected a COMEBACK mark, got $marks", cb != null)
        assertEquals(29, cb!!.moveNumber)
        assertEquals(DecisiveKind.GLOBAL, cb.kind)
    }

    @Test
    fun dragonFlipEarnsDing() {
        val size = BoardSize(9, 9)
        // A 9-stone white block in the corner.
        val dragon = (0..2).flatMap { x ->
            (0..2).map { y -> Triple(x, y, Stone.WHITE) }
        }.toTypedArray()
        val oneBoard = board(size, *dragon)
        val boards = List(14) { oneBoard }
        val mv = (1..13).map { n ->
            Move(if (n % 2 == 1) Stone.BLACK else Stone.WHITE, 5, 5)
        }
        fun own(dragonValue: Double): DoubleArray {
            val a = DoubleArray(size.area)
            for ((x, y, _) in dragon) a[y * size.width + x] = dragonValue
            return a
        }
        val ownership = mapOf(
            10 to own(0.0),   // undecided
            11 to own(0.95),  // black owns the dragon's points: dead
            12 to own(0.95),
            13 to own(0.95)
        )
        val marks = DecisiveMath.detect(
            size, mv, emptyMap(), ownership, boards, finalized = false
        )
        assertEquals(1, marks.size)
        assertEquals(11, marks[0].moveNumber)
        assertEquals(DecisiveKind.REGIONAL, marks[0].kind)
        assertEquals(DecisiveReason.DRAGON_KILL, marks[0].reason)
    }

    @Test
    fun dragonFlipWithoutPersistenceEarnsNothing() {
        val size = BoardSize(9, 9)
        val dragon = (0..2).flatMap { x ->
            (0..2).map { y -> Triple(x, y, Stone.WHITE) }
        }.toTypedArray()
        val oneBoard = board(size, *dragon)
        val boards = List(14) { oneBoard }
        val mv = (1..13).map { n ->
            Move(if (n % 2 == 1) Stone.BLACK else Stone.WHITE, 5, 5)
        }
        fun own(dragonValue: Double): DoubleArray {
            val a = DoubleArray(size.area)
            for ((x, y, _) in dragon) a[y * size.width + x] = dragonValue
            return a
        }
        // Ownership flips at 11 but flips back at 12: a mirage.
        val ownership = mapOf(
            10 to own(0.0), 11 to own(0.95), 12 to own(0.0), 13 to own(0.0)
        )
        val marks = DecisiveMath.detect(
            size, mv, emptyMap(), ownership, boards, finalized = false
        )
        assertTrue(marks.isEmpty())
    }

    @Test
    fun statFieldRoundTrip() {
        val marks = listOf(
            DecisiveMark(7, DecisiveKind.REGIONAL, DecisiveReason.DRAGON_KILL),
            DecisiveMark(7, DecisiveKind.GLOBAL, DecisiveReason.LOCK),
            DecisiveMark(9, DecisiveKind.KO, DecisiveReason.KO_RESOLVED)
        )
        // Global wins the single stored slot on a shared move.
        assertEquals(1 to 1, DecisiveMath.statFieldsFor(marks, 7))
        assertEquals(3 to 9, DecisiveMath.statFieldsFor(marks, 9))
        assertEquals(0 to 0, DecisiveMath.statFieldsFor(marks, 4))
    }
}
