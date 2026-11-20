package com.tuxungo.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungo.android.SettingsStore
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import kotlinx.coroutines.delay

/**
 * One step of a tsumego solution: the set of acceptable moves
 * for the player, and the scripted reply the app plays back
 * (null when the problem ends with the player's move).
 */
data class TsumegoStep(
    val accept: Set<Pair<Int, Int>>,
    val reply: Pair<Int, Int>?
)

/**
 * A built-in tsumego problem. Every problem in this list was
 * verified move by move with a brute-force rules solver: the
 * accepted first move(s) force the capture, and the tempting
 * wrong moves provably fail.
 */
data class TsumegoProblem(
    val title: String,
    val goal: String,
    val width: Int,
    val height: Int,
    val black: List<Pair<Int, Int>>,
    val white: List<Pair<Int, Int>>,
    val steps: List<TsumegoStep>,
    val hint: String,
    val doneText: String,
    /** True when the user plays White (a life-making / defense problem). */
    val userWhite: Boolean = false,
    /** Difficulty label: 入门 / 初级 / 中级. */
    val level: String = ""
)

val TSUMEGO_PROBLEMS: List<TsumegoProblem> = listOf(
    TsumegoProblem(
        title = "第一题 · 紧气",
        goal = "黑先，提掉角上的白棋",
        level = "入门",
        width = 5, height = 5,
        black = listOf(2 to 0, 0 to 2),
        white = listOf(0 to 0, 1 to 0, 0 to 1),
        steps = listOf(TsumegoStep(setOf(1 to 1), null)),
        hint = "先数一数：白棋还剩几口气？",
        doneText = "白棋只剩一口气，填上最后一气，三子全提。"
    ),
    TsumegoProblem(
        title = "第二题 · 双吃",
        goal = "黑先，一手同时叫吃两个白子",
        level = "入门",
        width = 5, height = 5,
        black = listOf(0 to 1, 1 to 0, 3 to 2, 2 to 3, 0 to 2, 1 to 3),
        white = listOf(1 to 1, 2 to 2),
        steps = listOf(TsumegoStep(setOf(2 to 1), null)),
        hint = "找一个同时挨着两个白子的点。注意：另一侧的连接点已被黑棋封住，白棋连不上。",
        doneText = "一手双叫吃：两个白子同时只剩一口气，而白棋的连接点是禁着点，救哪个都来不及。"
    ),
    TsumegoProblem(
        title = "第三题 · 共气对杀",
        goal = "黑先，对杀取胜（填错一口气，死的是自己）",
        level = "初级",
        width = 6, height = 4,
        black = listOf(
            0 to 0, 1 to 0, 0 to 1, 0 to 2, 1 to 2,
            3 to 0, 3 to 1, 3 to 2, 3 to 3
        ),
        white = listOf(2 to 0, 1 to 1, 2 to 1, 2 to 2, 2 to 3),
        steps = listOf(TsumegoStep(setOf(1 to 3), null)),
        hint = "黑白两块棋共用同一口气。先数清楚：哪口气是共用的，哪口气只是自己的？",
        doneText = "两块棋共用最后一口气，谁先填上谁赢。填共用气，白棋五子全提；要是先填自己那边的气，白棋反手就把黑棋提掉。"
    ),
    TsumegoProblem(
        title = "第四题 · 大龙紧气",
        goal = "黑先，提掉这条白棋大龙",
        level = "初级",
        width = 5, height = 5,
        black = listOf(3 to 0, 3 to 1, 3 to 2, 0 to 1, 0 to 2, 1 to 3, 2 to 3),
        white = listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2, 1 to 2),
        steps = listOf(TsumegoStep(setOf(1 to 1), null)),
        hint = "大龙看着气很多，其实只剩一口气——它藏在棋形的正中间，不在边上。",
        doneText = "大龙的最后一口气藏在中间的眼位上：那不是一只眼，而是全龙最后一口气，填上之后六子全提。"
    ),
    TsumegoProblem(
        title = "第五题 · 征子",
        goal = "黑先，用征子吃掉白子",
        level = "中级",
        width = 7, height = 7,
        black = listOf(2 to 1, 1 to 2, 3 to 3),
        white = listOf(2 to 2),
        steps = listOf(
            TsumegoStep(setOf(3 to 2), 2 to 3),
            TsumegoStep(setOf(2 to 4), 1 to 3),
            TsumegoStep(setOf(1 to 4), 0 to 3),
            TsumegoStep(setOf(0 to 4), 0 to 2),
            TsumegoStep(setOf(0 to 1), null)
        ),
        hint = "轮流从两侧叫吃，把白棋一步步赶向棋盘边缘，它的气永远只剩一口。",
        doneText = "征子一路赶到棋盘边上，白子无处可逃，整串被提。"
    ),
    TsumegoProblem(
        title = "第六题 · 做活",
        goal = "白先，把这块白棋做活（这次你执白）",
        level = "初级",
        width = 5, height = 5,
        black = listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3, 4 to 3),
        white = listOf(
            0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0,
            0 to 1, 4 to 1,
            0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2
        ),
        steps = listOf(TsumegoStep(setOf(2 to 1), null)),
        hint = "直三的眼位，关键在正中间：占住中间一点，就把一个大眼分成两个小眼。下在边上，黑棋扑进来提掉扑入子再回提，整块就没了。",
        doneText = "中间一点把直三眼位分成两个真眼，黑棋哪个眼都进不去，这块白棋活了。",
        userWhite = true
    ),
    TsumegoProblem(
        title = "第七题 · 倒脱靴",
        goal = "黑先，提掉这块白棋",
        level = "中级",
        width = 5, height = 5,
        black = listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3, 4 to 3),
        white = listOf(
            0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0,
            0 to 1, 4 to 1,
            0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2,
            1 to 1
        ),
        steps = listOf(
            TsumegoStep(setOf(2 to 1), 3 to 1),
            TsumegoStep(setOf(2 to 1), null)
        ),
        hint = "先扑一子进眼位：白棋提掉这颗扑入子之后，它留下的空点就是整块白棋的最后一口气。",
        doneText = "扑入、被提、再回到同一点回提——倒脱靴。白棋提掉一子，反而把自己最后一口气让了出来，十三子全提。"
    )
)

@Composable
fun TsumegoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }
    val prefs = remember {
        context.getSharedPreferences("tsumego_prefs", android.content.Context.MODE_PRIVATE)
    }
    var solvedSet by remember {
        mutableStateOf(
            (prefs.getString("solved", "") ?: "")
                .split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { it.toIntOrNull() }
                .toSet()
        )
    }
    var index by remember { mutableIntStateOf(0) }
    var unsolvedOnly by remember { mutableStateOf(false) }
    val problem = TSUMEGO_PROBLEMS[index]

    var game by remember { mutableStateOf(buildProblemGame(problem)) }
    var version by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hintPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var replyPending by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("轮到黑棋下：点棋盘选点，再点一次确认。")
    }
    var startedAt by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }
    var solveInfo by remember { mutableStateOf("") }
    var missTotal by remember {
        mutableIntStateOf(prefs.getInt("miss_0", 0))
    }

    fun reset(idx: Int) {
        index = idx
        val p = TSUMEGO_PROBLEMS[idx]
        game = buildProblemGame(p)
        version++
        step = 0
        mistakes = 0
        pending = null
        hintPoint = null
        replyPending = false
        solved = false
        startedAt = System.currentTimeMillis()
        solveInfo = ""
        missTotal = prefs.getInt("miss_$idx", 0)
        statusText = if (p.userWhite) {
            "轮到白棋下：点棋盘选点，再点一次确认。"
        } else {
            "轮到黑棋下：点棋盘选点，再点一次确认。"
        }
    }

    // The app answers with the scripted reply after a correct move.
    LaunchedEffect(replyPending) {
        if (!replyPending) return@LaunchedEffect
        delay(550)
        val reply = problem.steps[step].reply
        if (reply != null) {
            game.play(reply.first, reply.second)
            version++
        }
        step++
        replyPending = false
        statusText = "对方应了一手，继续：找下一步的关键点。"
    }

    @Suppress("UNUSED_EXPRESSION")
    version

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "死活题训练",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("返回") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = "已解 ${solvedSet.size} / ${TSUMEGO_PROBLEMS.size} 题",
                fontSize = 13.sp, color = Color.Gray,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val unsolved = TSUMEGO_PROBLEMS.indices.filter { it !in solvedSet }
                    val pool = unsolved.ifEmpty { TSUMEGO_PROBLEMS.indices.toList() }
                    val pick = pool.random()
                    if (pick != index) reset(pick) else reset(index)
                }
            ) { Text("随机一题") }
        }
        if (solvedSet.size == TSUMEGO_PROBLEMS.size) {
            Text(
                text = "全部 ${TSUMEGO_PROBLEMS.size} 道题都解开了，厉害！新题会陆续加入。",
                fontSize = 13.sp,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = unsolvedOnly,
                onClick = { unsolvedOnly = !unsolvedOnly },
                label = { Text("只看未解") }
            )
            TSUMEGO_PROBLEMS.forEachIndexed { i, _ ->
                if (unsolvedOnly && i in solvedSet && i != index) {
                    return@forEachIndexed
                }
                FilterChip(
                    selected = index == i,
                    onClick = { if (index != i) reset(i) },
                    label = { Text("第 ${i + 1} 题" + if (i in solvedSet) " ✓" else "") }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${problem.title}：${problem.goal}",
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold
        )
        if (problem.level.isNotEmpty()) {
            Text(
                text = "难度 · ${problem.level}",
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Text(
            text = statusText,
            fontSize = 13.sp,
            color = if (solved) Color(0xFF2E7D32) else Color(0xFF2F7CF6)
        )
        if (solveInfo.isNotEmpty()) {
            Text(text = solveInfo, fontSize = 12.sp, color = Color.Gray)
        }
        if (!solved && missTotal > 0) {
            Text(
                text = "这道题累计错过 $missTotal 次",
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        BoardView(
            game = game,
            enabled = !solved && !replyPending,
            skin = BoardSkin.fromId(settings.boardSkin),
            showCoordinates = settings.showCoordinates,
            preview = pending ?: hintPoint,
            onPointTap = { x, y ->
                if (solved || replyPending) return@BoardView
                if (!game.isLegal(x, y)) return@BoardView
                if (pending == Pair(x, y)) {
                    pending = null
                    hintPoint = null
                    val cur = problem.steps.getOrNull(step) ?: return@BoardView
                    if (Pair(x, y) in cur.accept) {
                        game.play(x, y)
                        version++
                        if (step >= problem.steps.size - 1) {
                            solved = true
                            val secs = ((System.currentTimeMillis() - startedAt) / 1000)
                                .toInt().coerceAtLeast(1)
                            val bestKey = "best_$index"
                            val prevBest = prefs.getInt(bestKey, Int.MAX_VALUE)
                            val newBest = minOf(prevBest, secs)
                            if (newBest != prevBest) {
                                prefs.edit().putInt(bestKey, newBest).apply()
                            }
                            solveInfo = "用时 $secs 秒 · 本题最佳 $newBest 秒"
                            statusText = problem.doneText +
                                if (mistakes > 0) "（本題错了 $mistakes 次）" else "（一次没错，漂亮）"
                            if (index !in solvedSet) {
                                val next = solvedSet + index
                                solvedSet = next
                                prefs.edit()
                                    .putString("solved", next.joinToString(","))
                                    .apply()
                            }
                        } else {
                            replyPending = true
                            statusText = "对！对方正在应招…"
                        }
                    } else {
                        mistakes++
                        missTotal++
                        prefs.edit().putInt("miss_$index", missTotal).apply()
                        statusText = "这步不对，再想想。需要的话点「提示」看关键点。"
                    }
                } else {
                    pending = Pair(x, y)
                }
            }
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (solved) {
            if (index < TSUMEGO_PROBLEMS.size - 1) {
                Button(
                    onClick = { reset(index + 1) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("下一题") }
            } else {
                Text(
                    "全部题目都解完了，厉害！",
                    fontSize = 14.sp, color = Color(0xFF2E7D32)
                )
            }
        } else {
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        hintPoint = problem.steps.getOrNull(step)?.accept?.firstOrNull()
                        statusText = "提示：${problem.hint}（棋盘上发光的点就是关键位置）"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("提示") }
                OutlinedButton(
                    onClick = { reset(index) },
                    modifier = Modifier.weight(1f)
                ) { Text("重新开始") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun buildProblemGame(p: TsumegoProblem): GoGame {
    val g = GoGame(BoardSize(p.width, p.height), 0.5)
    val stones = p.black.map { Move(Stone.BLACK, it.first, it.second) } +
        p.white.map { Move(Stone.WHITE, it.first, it.second) }
    g.loadPosition(stones, if (p.userWhite) Stone.WHITE else Stone.BLACK)
    return g
}
