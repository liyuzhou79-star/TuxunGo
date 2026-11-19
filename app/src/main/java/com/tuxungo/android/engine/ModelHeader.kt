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
     * (positive = Black owns it) — which is exactly the sign KataGo
     * v1.15.3's analysis engine returns (verified locally with corner
     * enclosures). [stones] is the board content in the same order
     * (null = empty). Only empty points count as territory.
     */
    fun estimate(ownership: DoubleArray, stones: Array<Stone?>): Result {
        var b = 0
        var w = 0
        var n = 0
        val count = minOf(ownership.size, stones.size)
        for (i in 0 until count) {
            if (stones[i] != null) continue
            val v = ownership[i]
            when {
                v > 0.5 -> b++
                v < -0.5 -> w++
                else -> n++
            }
        }
        return Result(b, w, n)
    }
}
