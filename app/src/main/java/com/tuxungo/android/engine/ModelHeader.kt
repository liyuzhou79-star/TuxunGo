package com.tuxungo.android.engine

import com.tuxungo.android.go.Stone

/**
 * Pure-Kotlin parsing of a KataGo text-format model header, used to show
 * what a user-uploaded model is before the engine test-loads it.
 *
 * Text model layout (verified against kata1-b10c128-s1141046784-d204142634):
 *   line 1: model name, e.g. "b10c128-s1141046784-d204142634"
 *   line 2: format version
 *   ...
 *   a line "trunk", followed by: numBlocks, then trunk channels.
 *
 * Binary (.bin) models have no text header; this parser returns null for
 * them and the engine test-load becomes the only validation.
 */
data class ModelHeader(
    val name: String,
    val numBlocks: Int?,
    val channels: Int?
) {
    fun describe(): String {
        val parts = mutableListOf<String>()
        if (numBlocks != null) parts.add("$numBlocks 层")
        if (channels != null) parts.add("$channels 通道")
        return if (parts.isEmpty()) "文本模型" else parts.joinToString(" · ")
    }
}

object ModelHeaderParser {

    private val NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._\\-]{1,99}$")

    /** [headText] is the first chunk of the (decompressed) model file. */
    fun parse(headText: String): ModelHeader? {
        val lines = headText.lineSequence()
            .take(40)
            .map { it.trim() }
            .toList()
        if (lines.isEmpty()) return null
        val name = lines[0]
        if (!NAME_PATTERN.matches(name)) return null
        // A text model has many short numeric lines early on; random binary
        // data would already have failed the name pattern in practice.
        var blocks: Int? = null
        var channels: Int? = null
        val trunkIdx = lines.indexOf("trunk")
        if (trunkIdx >= 0) {
            blocks = lines.getOrNull(trunkIdx + 1)?.toIntOrNull()
            channels = lines.getOrNull(trunkIdx + 2)?.toIntOrNull()
        }
        return ModelHeader(name = name, numBlocks = blocks, channels = channels)
    }
}

/** Territory estimate from KataGo ownership values. */
object TerritoryEstimate {

    data class Result(val blackTerritory: Int, val whiteTerritory: Int, val neutral: Int)

    /** A whole empty region whose mean ownership passes this counts entirely for that side. */
    private const val REGION_MEAN = 0.55

    /** A stone group whose mean ownership passes this (against its owner) is dead. */
    private const val GROUP_MEAN = 0.35

    /** A single stone owned this strongly by the opponent is dead even inside a live group. */
    private const val STONE_CERTAIN = 0.9

    private fun neighbors(i: Int, width: Int, height: Int): List<Int> {
        val x = i % width
        val y = i / width
        val out = ArrayList<Int>(4)
        if (x > 0) out.add(i - 1)
        if (x < width - 1) out.add(i + 1)
        if (y > 0) out.add(i - width)
        if (y < height - 1) out.add(i + width)
        return out
    }

    /**
     * Which stones are dead, judged GROUP by group. Per-point
     * ownership is noisy — one point of a live group often leans the
     * wrong way — so a connected group is judged by the MEAN
     * ownership of its stones' points: a group whose points on
     * average belong to the opponent is dead. A single stone owned
     * overwhelmingly by the opponent ([STONE_CERTAIN]) is dead even
     * when its group lives (a cut-off tail, a lone invader).
     *
     * [ownership] is row-major from the top-left, in BLACK's
     * perspective (positive = Black owns the point); [stones] is the
     * board content in the same order (null = empty).
     */
    fun deadStones(
        ownership: DoubleArray,
        stones: Array<Stone?>,
        width: Int,
        height: Int,
        groupMean: Double = GROUP_MEAN
    ): Set<Int> {
        val n = minOf(ownership.size, stones.size, width * height)
        val dead = mutableSetOf<Int>()
        val visited = BooleanArray(n)
        for (i in 0 until n) {
            val s = stones[i] ?: continue
            val v = ownership[i]
            if ((s == Stone.BLACK && v <= -STONE_CERTAIN) ||
                (s == Stone.WHITE && v >= STONE_CERTAIN)
            ) {
                dead.add(i)
            }
            if (visited[i]) continue
            val group = mutableListOf<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(i)
            visited[i] = true
            var sum = 0.0
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                group.add(cur)
                sum += ownership[cur]
                for (nb in neighbors(cur, width, height)) {
                    if (!visited[nb] && stones[nb] == s) {
                        visited[nb] = true
                        stack.add(nb)
                    }
                }
            }
            val mean = sum / group.size
            if ((s == Stone.BLACK && mean < -groupMean) ||
                (s == Stone.WHITE && mean > groupMean)
            ) {
                dead.addAll(group)
            }
        }
        return dead
    }

    /**
     * Counts territory (empty points plus dead stones' points),
     * Chinese-scoring style. Dead groups come from [deadStones];
     * their points are then treated as empty. Each connected empty
     * region is judged by its mean ownership first — a solid region
     * counts in full even if a point or two inside reads weakly —
     * and only genuinely mixed regions fall back to per-point
     * counting at [threshold].
     */
    fun estimate(
        ownership: DoubleArray,
        stones: Array<Stone?>,
        width: Int,
        height: Int,
        threshold: Double = 0.5
    ): Result {
        val n = minOf(ownership.size, stones.size, width * height)
        val dead = deadStones(ownership, stones, width, height)
        var b = 0
        var w = 0
        var neutral = 0
        val visited = BooleanArray(n)
        for (i in 0 until n) {
            val s = stones[i]
            if (s != null && i !in dead) continue // a live stone is not territory
            if (visited[i]) continue
            val region = mutableListOf<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(i)
            visited[i] = true
            var sum = 0.0
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                region.add(cur)
                sum += ownership[cur]
                for (nb in neighbors(cur, width, height)) {
                    if (visited[nb]) continue
                    val ns = stones[nb]
                    if (ns == null || nb in dead) {
                        visited[nb] = true
                        stack.add(nb)
                    }
                }
            }
            val mean = sum / region.size
            when {
                mean > REGION_MEAN -> b += region.size
                mean < -REGION_MEAN -> w += region.size
                else -> for (p in region) {
                    val pst = stones[p]
                    when {
                        p in dead && pst == Stone.WHITE -> b++
                        p in dead && pst == Stone.BLACK -> w++
                        ownership[p] > threshold -> b++
                        ownership[p] < -threshold -> w++
                        else -> neutral++
                    }
                }
            }
        }
        return Result(b, w, neutral)
    }
}
