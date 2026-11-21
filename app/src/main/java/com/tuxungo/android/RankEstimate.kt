package com.tuxungo.android

import com.tuxungo.android.engine.SideAiStore

/**
 * A rough playing-strength estimate from what the app already
 * knows: results against the ranked AI ladder, plus how much of
 * the tsumego set is solved. Deliberately a RANGE — a single
 * number would pretend to a precision this data does not have.
 * The "max" rung is never used: estimates stay on the ranked
 * ladder (9d .. 18k).
 */
object RankEstimate {

    data class Result(
        /** Rank ids (SideAiStore.RANKS), weaker / stronger ends. */
        val lowRank: String,
        val highRank: String,
        val basis: List<String>
    )

    fun estimate(
        tallies: Map<String, IntArray>,
        tsumegoSolved: Int,
        tsumegoTotal: Int
    ): Result? {
        val ladder = SideAiStore.RANKS.drop(1)
        fun idx(rank: String) = ladder.indexOf(rank)
        val played = tallies
            .filterKeys { idx(it) >= 0 }
            .filterValues { it[0] + it[1] > 0 }
        val basis = mutableListOf<String>()
        val centre: Int
        if (played.isEmpty()) {
            if (tsumegoTotal > 0 && tsumegoSolved >= 5) {
                centre = idx("12k")
                basis.add("还没有定级对局记录，先按死活题完成度粗估")
            } else {
                return null
            }
        } else {
            val bestBeaten = played.entries
                .filter { it.value[0] > 0 }
                .minByOrNull { idx(it.key) }
            if (bestBeaten != null) {
                var c = idx(bestBeaten.key)
                val w = bestBeaten.value[0]
                val l = bestBeaten.value[1]
                basis.add(
                    "赢过最强的 AI 档位：" +
                        "${SideAiStore.rankLabel(bestBeaten.key)}（$w 胜 $l 负）"
                )
                if (l == 0 && w >= 2) {
                    c = (c - 1).coerceAtLeast(0)
                    basis.add("在这一档保持全胜，可能还低估了你")
                }
                centre = c
            } else {
                val weakestTried = played.keys.maxByOrNull { idx(it) }!!
                centre = (idx(weakestTried) + 1)
                    .coerceAtMost(ladder.size - 1)
                basis.add(
                    "暂时还没赢下定级局" +
                        "（打过最弱的一档是 ${SideAiStore.rankLabel(weakestTried)}）"
                )
            }
            val totalW = played.values.sumOf { it[0] }
            val totalL = played.values.sumOf { it[1] }
            basis.add("定级对局总战绩：$totalW 胜 $totalL 负")
        }
        if (tsumegoTotal > 0) {
            basis.add("死活题完成 $tsumegoSolved / $tsumegoTotal")
        }
        val lo = (centre + 2).coerceAtMost(ladder.size - 1)
        val hi = (centre - 2).coerceAtLeast(0)
        return Result(ladder[lo], ladder[hi], basis)
    }
}
