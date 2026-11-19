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

/** Rough territory estimate from KataGo ownership values. */
object TerritoryEstimate {

    data class Result(val blackTerritory: Int, val whiteTerritory: Int, val neutral: Int)

    /**
     * [ownership] has one value per board point in row-major order from
     * the top-left (index = y * width + x), in BLACK's perspective
     * (positive = Black owns it). Note KataGo itself reports ownership
     * from the side-to-move perspective; [AnalysisEngine] converts it
     * to Black's perspective before it reaches this function.
     * [stones] is the board content in the same order (null = empty).
     *
     * Counting follows Chinese scoring intuition: an empty point owned
     * above [threshold] counts for its owner, and a stone the engine
     * judges dead (its point is owned by the opponent above
     * [threshold]) also counts for the opponent — the point becomes
     * the opponent's territory once the dead stone is removed.
     */
    fun estimate(ownership: DoubleArray, stones: Array<Stone?>, threshold: Double = 0.5): Result {
        var b = 0
        var w = 0
        var n = 0
        val count = minOf(ownership.size, stones.size)
        for (i in 0 until count) {
            val v = ownership[i]
            val stone = stones[i]
            when {
                stone == null && v > threshold -> b++
                stone == null && v < -threshold -> w++
                stone == Stone.WHITE && v > threshold -> b++ // dead white stone
                stone == Stone.BLACK && v < -threshold -> w++ // dead black stone
                stone == null -> n++
            }
        }
        return Result(b, w, n)
    }
}
