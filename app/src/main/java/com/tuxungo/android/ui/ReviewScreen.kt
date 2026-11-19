package com.tuxungo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungo.android.LibraryGame
import com.tuxungo.android.LibraryStore
import com.tuxungo.android.SettingsStore
import com.tuxungo.android.engine.AnalysisEngine
import com.tuxungo.android.engine.EngineAssets
import com.tuxungo.android.engine.KataEngine
import com.tuxungo.android.engine.MarkKind
import com.tuxungo.android.engine.ModelStore
import com.tuxungo.android.engine.MoveMark
import com.tuxungo.android.engine.ReviewMath
import com.tuxungo.android.engine.TurnStat
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-game review: per-move win rates, a scrubbable replay board,
 * blunder / brilliant / key-move marks, and a win-rate chart whose
 * green bands mark stretches where one side held 75%+.
 */
@Composable
fun ReviewScreen(
    game: LibraryGame,
    onBack: () -> Unit,
    onContinue: (GameConfig) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }
    var current by remember { mutableStateOf(game) }
    val totalMoves = current.moves.size

    var turns by remember {
        mutableStateOf(
            current.statsCache?.let { ReviewMath.decodeTurns(it) }
                ?.takeIf { it.size == totalMoves + 1 }
        )
    }
    var analyzing by remember { mutableStateOf(false) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(totalMoves + 1) }
    var errorText by remember { mutableStateOf("") }
    var perspective by remember { mutableStateOf(Stone.BLACK) }
    var ply by remember { mutableIntStateOf(totalMoves) }
    var showBest by remember { mutableStateOf(true) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var savedNote by remember { mutableStateOf(false) }

    val runner = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { runner.shutdownNow() } }

    fun setupStonesOf(g: LibraryGame): List<Move> = when {
        g.customStones.isNotEmpty() -> g.customStones
        g.handicap > 0 -> {
            val tmp = GoGame(g.size, g.komi)
            tmp.setupHandicap(g.handicap)
            val out = mutableListOf<Move>()
            for (y in 0 until g.size.height) {
                for (x in 0 until g.size.width) {
                    tmp.stoneAt(x, y)?.let { out.add(Move(it, x, y)) }
                }
            }
            out
        }
        else -> emptyList()
    }

    fun startAnalysis() {
        if (analyzing || totalMoves == 0) return
        analyzing = true
        errorText = ""
        progressDone = 0
        progressTotal = totalMoves + 1
        val g = current
        runner.execute {
            var eng: AnalysisEngine? = null
            try {
                val model = ModelStore.resolveActiveFile(context)
                eng = AnalysisEngine.start(
                    context.applicationInfo.nativeLibraryDir,
                    EngineAssets.engineWorkDir(context),
                    model
                )
                val initPlayer = when {
                    g.customStones.isNotEmpty() -> g.startPlayer
                    g.handicap > 0 -> Stone.WHITE
                    else -> Stone.BLACK
                }
                val result = eng.analyzeGame(
                    size = g.size,
                    komi = g.komi,
                    initialStones = setupStonesOf(g),
                    initialPlayer = initPlayer,
                    moves = g.moves,
                    sideToMove = if (g.moves.isEmpty()) initPlayer else g.moves.last().stone.other(),
                    maxVisits = 100,
                    onProgress = { d, t ->
                        progressDone = d
                        progressTotal = t
                    }
                )
                turns = result
                if (g.id.isNotBlank()) {
                    LibraryStore.updateStatsCache(context, g.id, ReviewMath.encodeTurns(result))
                }
            } catch (e: Exception) {
                errorText = "整场分析失败：${e.message ?: "未知错误"}"
            } finally {
                try {
                    eng?.quit()
                } catch (_: Exception) {
                }
                analyzing = false
            }
        }
    }

    LaunchedEffect(current.id) {
        if (turns == null && totalMoves > 0) startAnalysis()
    }

    val marks = remember(turns) {
        turns?.let { ReviewMath.marks(current.moves, it) } ?: emptyList()
    }

    val replayGame = remember(ply, current.id) {
        GoGame(current.size, current.komi).also { g ->
            when {
                current.customStones.isNotEmpty() ->
                    g.loadPosition(current.customStones, current.startPlayer)
                current.handicap > 0 -> g.setupHandicap(current.handicap)
            }
            for (i in 0 until ply.coerceAtMost(current.moves.size)) {
                val m = current.moves[i]
                if (m.isPass) g.pass() else g.play(m.x, m.y)
            }
        }
    }

    val bestMark: List<CandidateMark> = remember(turns, ply, showBest) {
        val t = turns
        if (!showBest || t == null || ply >= totalMoves) {
            emptyList()
        } else {
            val stat = t[ply]
            val bm = stat.bestMove
            if (!stat.bestIsPass && bm != null) {
                listOf(CandidateMark(bm.x, bm.y, 1))
            } else {
                emptyList()
            }
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(modifier = Modifier.weight(1f))
            if (current.id.isBlank()) {
                TextButton(onClick = { showSaveDialog = true }) { Text("收藏棋谱") }
            } else if (savedNote) {
                Text("已收藏", fontSize = 13.sp, color = Color.Gray)
            }
        }
        Text(current.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "${current.modeLabel} · ${current.resultText.ifBlank { "无结果记录" }} · 共 $totalMoves 手",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))

        BoardView(
            game = replayGame,
            enabled = false,
            skin = BoardSkin.fromId(settings.boardSkin),
            showCoordinates = settings.showCoordinates,
            territoryThreshold = 0.5,
            candidates = bestMark,
            ownership = null,
            pvGhost = emptyList(),
            preview = null,
            markedDead = emptySet()
        ) { _, _ -> }
        Spacer(modifier = Modifier.height(4.dp))

        Row {
            TextButton(
                onClick = { ply = (ply - 1).coerceAtLeast(0) },
                enabled = ply > 0
            ) { Text("上一手") }
            Text(
                "第 $ply / $totalMoves 手",
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp)
            )
            TextButton(
                onClick = { ply = (ply + 1).coerceAtMost(totalMoves) },
                enabled = ply < totalMoves
            ) { Text("下一手") }
        }
        Slider(
            value = ply.toFloat(),
            onValueChange = { ply = it.roundToInt() },
            valueRange = 0f..totalMoves.coerceAtLeast(1).toFloat()
        )

        val stat = turns?.getOrNull(ply)
        if (stat != null) {
            val wr = ReviewMath.winrateFor(stat, perspective)
            val leadP = if (perspective == Stone.BLACK) {
                stat.scoreLeadBlack
            } else {
                -stat.scoreLeadBlack
            }
            val sideName = if (perspective == Stone.BLACK) "黑" else "白"
            val leadText = when {
                leadP >= 0 -> "$sideName 领先约 ${fmtNum(abs(leadP))} 目"
                else -> "${if (perspective == Stone.BLACK) "白" else "黑"} 领先约 ${fmtNum(abs(leadP))} 目"
            }
            Text(
                "第 $ply 手：${sideName}方胜率 ${fmtPct(wr)} · $leadText",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            if (ply < totalMoves) {
                val best = turns?.getOrNull(ply)
                val bestText = when {
                    best == null -> ""
                    best.bestIsPass -> "停一手"
                    best.bestMove != null -> KataEngine.toVertex(best.bestMove, current.size)
                    else -> ""
                }
                if (bestText.isNotEmpty()) {
                    val actual = current.moves[ply]
                    val actualText = if (actual.isPass) {
                        "停一手"
                    } else {
                        KataEngine.toVertex(actual, current.size)
                    }
                    Text(
                        "AI 一选：$bestText · 实战：$actualText",
                        fontSize = 13.sp, color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row {
            FilterChip(
                selected = perspective == Stone.BLACK,
                onClick = { perspective = Stone.BLACK },
                label = { Text("黑方视角") }
            )
            Spacer(modifier = Modifier.padding(start = 8.dp))
            FilterChip(
                selected = perspective == Stone.WHITE,
                onClick = { perspective = Stone.WHITE },
                label = { Text("白方视角") }
            )
            Spacer(modifier = Modifier.padding(start = 8.dp))
            FilterChip(
                selected = showBest,
                onClick = { showBest = !showBest },
                label = { Text("显示一选") }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        when {
            turns != null -> {
                WinrateChart(
                    turns = turns!!,
                    perspective = perspective,
                    ply = ply,
                    marks = marks,
                    onSeek = { ply = it }
                )
                Text(
                    "红线：胜率 · 黄线：目差 · 绿色带：胜率 75% 以上的优势时段 · 拖动曲线或滑块回放",
                    fontSize = 11.sp, color = Color.Gray
                )
            }
            analyzing -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("正在分析整场对局…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (progressTotal > 0) {
                                    progressDone.toFloat() / progressTotal
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "已完成 $progressDone / $progressTotal 手，分析完会自动保存，下次打开直接看。",
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                }
            }
            errorText.isNotEmpty() -> {
                Text(errorText, fontSize = 13.sp, color = Color(0xFFB3261E))
                OutlinedButton(onClick = { startAnalysis() }) { Text("重新分析") }
            }
            totalMoves == 0 -> {
                Text("这局还没有落子记录，没法复盘。", fontSize = 13.sp, color = Color.Gray)
            }
        }

        if (turns != null) {
            Spacer(modifier = Modifier.height(10.dp))
            val sideMarks = marks.filter {
                current.moves.getOrNull(it.moveNumber - 1)?.stone == perspective
            }
            val blunders = sideMarks.count { it.kind == MarkKind.BLUNDER }
            val brilliants = sideMarks.count { it.kind == MarkKind.BRILLIANT }
            val keyMark = marks.firstOrNull { it.kind == MarkKind.KEY }
            val sideName = if (perspective == Stone.BLACK) "黑" else "白"
            Text(
                "本局纪录（${sideName}方）",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "恶手 $blunders 个 · 妙手 $brilliants 个" +
                    (keyMark?.let { " · 全场波动最大：第 ${it.moveNumber} 手（${fmtDelta(it.deltaPercent)}）" } ?: ""),
                fontSize = 13.sp, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (sideMarks.isEmpty()) {
                Text("这一方没有被标出的恶手或妙手。", fontSize = 13.sp, color = Color.Gray)
            }
            for (mark in sideMarks.sortedByDescending { abs(it.deltaPercent) }) {
                val (label, color) = when (mark.kind) {
                    MarkKind.BLUNDER -> "恶手" to Color(0xFFC62828)
                    MarkKind.BRILLIANT -> "妙手" to Color(0xFF2E7D32)
                    MarkKind.KEY -> "关键手" to Color(0xFF6A1B9A)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(label, fontSize = 14.sp, color = color, fontWeight = FontWeight.SemiBold)
                    Text(
                        " 第 ${mark.moveNumber} 手 · 胜率 ${fmtDelta(mark.deltaPercent)}",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { ply = mark.moveNumber }) { Text("跳到这手") }
                }
            }
            Text(
                "判定：胜率掉 15% 以上为恶手；下出 AI 一选且不亏为妙手；全场胜率波动最大的一手为关键手。",
                fontSize = 11.sp, color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val stones = mutableListOf<Move>()
                for (y in 0 until current.size.height) {
                    for (x in 0 until current.size.width) {
                        replayGame.stoneAt(x, y)?.let { stones.add(Move(it, x, y)) }
                    }
                }
                onContinue(
                    GameConfig(
                        mode = GameMode.PVP,
                        width = current.size.width,
                        height = current.size.height,
                        komi = current.komi,
                        customStones = stones,
                        startPlayer = replayGame.current
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("从这手继续下") }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showSaveDialog) {
        SaveGameDialog(
            game = current,
            onDismiss = { showSaveDialog = false },
            onSaved = { saved ->
                current = saved
                savedNote = true
                showSaveDialog = false
                if (turns != null) {
                    LibraryStore.updateStatsCache(
                        context, saved.id, ReviewMath.encodeTurns(turns!!)
                    )
                }
            }
        )
    }
}

@Composable
private fun WinrateChart(
    turns: List<TurnStat>,
    perspective: Stone,
    ply: Int,
    marks: List<MoveMark>,
    onSeek: (Int) -> Unit
) {
    val total = (turns.size - 1).coerceAtLeast(1)
    val winColor = Color(0xFFB03A2E)
    val leadColor = Color(0xFFE6A817)
    val greenFill = Color(0xFF43A047).copy(alpha = 0.30f)
    val gridColor = Color.Gray.copy(alpha = 0.35f)
    val cursorColor = Color(0xFF6A1B9A)

    fun seek(x: Float, w: Float) {
        if (w <= 0f) return
        onSeek((x / w * total).roundToInt().coerceIn(0, total))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(total) {
                detectTapGestures { offset -> seek(offset.x, size.width.toFloat()) }
            }
            .pointerInput(total) {
                detectHorizontalDragGestures { change, _ ->
                    seek(change.position.x, size.width.toFloat())
                }
            }
    ) {
        val w = size.width
        val h = size.height
        fun xOf(turn: Int) = turn.toFloat() / total * w
        fun yWr(wr: Double) = (1f - wr.toFloat()) * h
        fun yLead(lead: Double): Float {
            val c = lead.coerceIn(-50.0, 50.0)
            return (h / 2f - (c / 50.0).toFloat() * h / 2f)
        }

        // Grid lines at 25 / 50 / 75 percent.
        for (p in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(
                color = gridColor,
                start = Offset(0f, p * h),
                end = Offset(w, p * h),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
        }

        // Green dominance bands: win rate at or above 75%.
        var runStart = -1
        fun flushRun(endTurn: Int) {
            if (runStart < 0) return
            val path = Path()
            path.moveTo(xOf(runStart), yWr(0.75))
            for (t in runStart..endTurn) {
                path.lineTo(xOf(t), yWr(ReviewMath.winrateFor(turns[t], perspective)))
            }
            path.lineTo(xOf(endTurn), yWr(0.75))
            path.close()
            drawPath(path, greenFill)
            runStart = -1
        }
        for (t in turns.indices) {
            val above = ReviewMath.winrateFor(turns[t], perspective) >= 0.75
            if (above && runStart < 0) runStart = t
            if (!above && runStart >= 0) flushRun(t - 1)
        }
        if (runStart >= 0) flushRun(turns.size - 1)

        // Score-lead line (perspective-adjusted).
        val leadPath = Path()
        for (t in turns.indices) {
            val lead = if (perspective == Stone.BLACK) {
                turns[t].scoreLeadBlack
            } else {
                -turns[t].scoreLeadBlack
            }
            val x = xOf(t)
            val y = yLead(lead)
            if (t == 0) leadPath.moveTo(x, y) else leadPath.lineTo(x, y)
        }
        drawPath(leadPath, leadColor, style = Stroke(width = 2.5f))

        // Win-rate line.
        val wrPath = Path()
        for (t in turns.indices) {
            val x = xOf(t)
            val y = yWr(ReviewMath.winrateFor(turns[t], perspective))
            if (t == 0) wrPath.moveTo(x, y) else wrPath.lineTo(x, y)
        }
        drawPath(wrPath, winColor, style = Stroke(width = 3f))

        // Mark dots.
        for (mark in marks) {
            val t = mark.moveNumber.coerceIn(0, total)
            val center = Offset(
                xOf(t),
                yWr(ReviewMath.winrateFor(turns[t], perspective))
            )
            val (color, radius) = when (mark.kind) {
                MarkKind.BLUNDER -> Color(0xFFC62828) to 6f
                MarkKind.BRILLIANT -> Color(0xFF2E7D32) to 6f
                MarkKind.KEY -> Color(0xFF6A1B9A) to 8f
            }
            drawCircle(color = Color.White, radius = radius + 2f, center = center)
            drawCircle(color = color, radius = radius, center = center)
        }

        // Cursor.
        val cx = xOf(ply.coerceIn(0, total))
        drawLine(cursorColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 2f)
        drawCircle(
            color = cursorColor,
            radius = 6f,
            center = Offset(
                cx,
                yWr(ReviewMath.winrateFor(turns[ply.coerceIn(0, total)], perspective))
            )
        )
    }
}

private fun fmtPct(v: Double): String = String.format("%.1f%%", v * 100)

private fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

private fun fmtDelta(v: Double): String =
    String.format("%+.1f%%", v)
