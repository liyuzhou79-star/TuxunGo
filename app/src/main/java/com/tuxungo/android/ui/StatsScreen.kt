package com.tuxungo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungo.android.LibraryStore
import com.tuxungo.android.RankEstimate
import com.tuxungo.android.RankedStore
import com.tuxungo.android.engine.SideAiStore

/**
 * Personal statistics, computed from the local game library plus the
 * training modes' saved progress: totals, black/white win split,
 * per-mode counts, a move-count chart of recent games, and training
 * progress. Everything is local; nothing leaves the device.
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val games = remember { LibraryStore.listGames(context) }
    val finished = games.filter { it.resultText.isNotBlank() }
    val blackWins = finished.count { it.resultText.startsWith("黑") }
    val whiteWins = finished.count { it.resultText.startsWith("白") }
    val totalMoves = games.sumOf { it.moves.size }
    val avgMoves = if (games.isNotEmpty()) totalMoves / games.size else 0
    val longest = games.maxByOrNull { it.moves.size }
    val recent = games.sortedBy { it.createdAt }.takeLast(14)
    val tsumegoSolved = remember {
        val p = context.getSharedPreferences(
            "tsumego_prefs", android.content.Context.MODE_PRIVATE
        )
        p.getString("solved", "")
            ?.split(",")
            ?.count { it.isNotBlank() } ?: 0
    }
    val guessBest = remember {
        val p = context.getSharedPreferences(
            "guess_prefs", android.content.Context.MODE_PRIVATE
        )
        p.getInt("bestScore", 0) to p.getInt("bestStreak", 0)
    }
    val guessMistakes = remember {
        val p = context.getSharedPreferences(
            "guess_prefs", android.content.Context.MODE_PRIVATE
        )
        val s = p.getString("mistakes", "") ?: ""
        if (s.isBlank()) 0 else s.split("|").count { it.isNotBlank() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("我的数据", "对局总览、黑白胜负和训练进度", onBack)
        Spacer(modifier = Modifier.height(4.dp))

        val rankEstimate = remember {
            RankEstimate.estimate(
                RankedStore.loadAll(context),
                tsumegoSolved,
                TSUMEGO_PROBLEMS.size
            )
        }
        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "棋力自测",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (rankEstimate == null) {
                    Text(
                        text = "数据还不够：去打一盘定级对局（开局设置里给 AI " +
                            "选个段位），或者先做几道死活题，这里就能估出" +
                            "你的大致棋力。",
                        fontSize = 13.sp, color = Color.Gray
                    )
                } else {
                    Text(
                        text = "估计棋力：约 " +
                            SideAiStore.rankLabel(rankEstimate.highRank) +
                            " ~ " +
                            SideAiStore.rankLabel(rankEstimate.lowRank),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    for (line in rankEstimate.basis) {
                        Text(
                            text = "· $line",
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                    Text(
                        text = "按 App 内的定级 AI 和训练数据粗估，只是个参考区间。",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "对局总览", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                if (games.isEmpty()) {
                    EmptyState(
                        title = "还没有对局记录",
                        desc = "去下一盘，这里就会有数据了。"
                    )
                } else {
                    StatRow("棋谱总数", "${games.size} 局")
                    StatRow("已下完", "${finished.size} 局")
                    StatRow("进行中存档", "${games.size - finished.size} 局")
                    StatRow("总手数", "$totalMoves 手")
                    StatRow("平均每局", "$avgMoves 手")
                    if (longest != null) {
                        StatRow("最长一局", "${longest.moves.size} 手（${longest.modeLabel}）")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (finished.isNotEmpty()) {
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "胜负分布", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val pct = blackWins * 100 / finished.size
                    StatRow("黑胜", "$blackWins 局（$pct%）")
                    StatRow("白胜", "$whiteWins 局（${100 - pct}%）")
                    Spacer(modifier = Modifier.height(6.dp))
                    val modes = finished.groupBy { it.modeLabel }
                    for ((mode, list) in modes) {
                        StatRow(mode, "${list.size} 局")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (recent.size >= 2) {
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "最近对局手数", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val maxMoves = recent.maxOf { it.moves.size }.coerceAtLeast(1)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                    ) {
                        val gap = 4.dp.toPx()
                        val barW = (size.width - gap * (recent.size - 1)) / recent.size
                        recent.forEachIndexed { i, g ->
                            val h = size.height * g.moves.size / maxMoves
                            drawRect(
                                color = Color(0xFF607D8B),
                                topLeft = Offset(i * (barW + gap), size.height - h),
                                size = Size(barW, h)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "最近 ${recent.size} 局，最长 ${recent.maxOf { it.moves.size }} 手",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        GameCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "训练进度", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                StatRow("死活题已解", "$tsumegoSolved / ${TSUMEGO_PROBLEMS.size} 题")
                StatRow(
                    "猜一选历史最佳",
                    if (guessBest.first > 0 || guessBest.second > 0) {
                        "得分 ${guessBest.first} · 连对 ${guessBest.second}"
                    } else {
                        "还没玩过"
                    }
                )
                StatRow(
                    "猜一选错题待复习",
                    if (guessMistakes > 0) "$guessMistakes 题" else "暂无"
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "数据只来自保存在本机的棋谱库和训练记录，不联网、不上传。",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(text = label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
