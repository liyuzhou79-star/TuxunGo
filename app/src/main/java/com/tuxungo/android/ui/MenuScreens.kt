package com.tuxungo.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared screen chrome: a back button with a chevron, a bold
 * title and an optional grey subtitle — every secondary screen
 * uses it, so navigation looks and feels the same everywhere.
 */
@Composable
fun ScreenHeader(title: String, subtitle: String = "", onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹ 返回", fontSize = 15.sp) }
        Spacer(modifier = Modifier.width(2.dp))
        Column {
            Text(text = title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

/** Section header with a small accent bar. */
@Composable
fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 17.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Navigation card used in the training / library grids.
 * Shrinks slightly while pressed, for tactile feedback. */
@Composable
fun NavCard(
    title: String,
    desc: String,
    foot: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        label = "navCardPress"
    )
    Card(
        modifier = modifier
            .height(118.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, fontSize = 12.sp, color = Color.Gray, maxLines = 2)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = foot,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
            )
        }
    }
}

/** Wide menu card (home page entries, tool entries): title,
 * description, a status line and a chevron. Press feedback
 * matches [NavCard]. */
@Composable
fun MenuCard(
    title: String,
    desc: String,
    foot: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        label = "menuCardPress"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = desc, fontSize = 13.sp, color = Color.Gray)
                if (foot.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = foot, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )
                }
            }
            Text(text = "›", fontSize = 24.sp, color = Color.Gray)
        }
    }
}

/** Second-level page: the two training modes, moved off the
 * home screen so the home screen stays short. */
@Composable
fun TrainingScreen(
    onBack: () -> Unit,
    onOpenGuess: () -> Unit,
    onOpenTsumego: (Int) -> Unit
) {
    val context = LocalContext.current
    val guessBestFoot = remember {
        val p = context.getSharedPreferences(
            "guess_prefs", android.content.Context.MODE_PRIVATE
        )
        val bs = p.getInt("bestScore", 0)
        val bk = p.getInt("bestStreak", 0)
        if (bs > 0 || bk > 0) "最佳 $bs 分 · 连对 $bk" else "暂无成绩"
    }
    val tsumegoSolvedSet = remember {
        val s = context.getSharedPreferences(
            "tsumego_prefs", android.content.Context.MODE_PRIVATE
        ).getString("solved", "") ?: ""
        if (s.isBlank()) {
            emptySet()
        } else {
            s.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        }
    }
    // Daily problem: rotate through the unsolved ones by day of
    // year, so the card always recommends a fresh problem.
    val dailyTsumego = remember {
        val unsolved = TSUMEGO_PROBLEMS.indices.filter {
            it !in tsumegoSolvedSet
        }
        if (unsolved.isEmpty()) {
            -1
        } else {
            val day = java.util.Calendar.getInstance()
                .get(java.util.Calendar.DAY_OF_YEAR)
            unsolved[day % unsolved.size]
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ScreenHeader("训练", "两种练棋方式，慢慢涨棋", onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            NavCard(
                title = "猜一选",
                desc = "猜 AI 每一步的一选，练棋感和大局观",
                foot = guessBestFoot,
                modifier = Modifier.weight(1f),
                onClick = onOpenGuess
            )
            NavCard(
                title = "死活题",
                desc = "紧气、双吃、对杀、征子 · 已解 ${tsumegoSolvedSet.size}/${TSUMEGO_PROBLEMS.size}",
                foot = if (dailyTsumego >= 0) {
                    "今日一题：${TSUMEGO_PROBLEMS[dailyTsumego].title}"
                } else {
                    "全部解完，太强了"
                },
                modifier = Modifier.weight(1f),
                onClick = {
                    onOpenTsumego(if (dailyTsumego >= 0) dailyTsumego else 0)
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "猜一选猜错的题会进错题本，可以专门复习；死活题每天推荐一道没解过的。",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}

/** Second-level page: library, stats, models and the physical
 * board clock, moved off the home screen. */
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenPhysicalClock: () -> Unit
) {
    val context = LocalContext.current
    val activeModelName = remember {
        val models = com.tuxungo.android.engine.ModelStore.list(context)
        val activeId = com.tuxungo.android.engine.ModelStore.activeId(context)
        models.firstOrNull { it.id == activeId }?.displayName ?: "轻量模型"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        ScreenHeader("资料与工具", "棋谱、数据、模型和棋钟", onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            NavCard(
                title = "棋谱库",
                desc = "收藏的对局都在这里，点开就能复盘",
                foot = "打开棋谱库",
                modifier = Modifier.weight(1f),
                onClick = onOpenLibrary
            )
            NavCard(
                title = "我的数据",
                desc = "对局总览、黑白胜负和训练进度",
                foot = "查看统计",
                modifier = Modifier.weight(1f),
                onClick = onOpenStats
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        MenuCard(
            title = "AI 模型",
            desc = "导入更强的 KataGo 模型，或给模型测速",
            foot = "当前模型：$activeModelName",
            onClick = onOpenModels
        )
        Spacer(modifier = Modifier.height(12.dp))
        MenuCard(
            title = "实体棋盘棋钟",
            desc = "两个人用真棋盘下棋，手机当棋钟：下完一步点自己这边",
            foot = "打开棋钟",
            onClick = onOpenPhysicalClock
        )
    }
}
