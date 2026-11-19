package com.tuxungo.android.ui

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tuxungo.android.engine.AnalysisEngine
import com.tuxungo.android.engine.AnalysisResult
import com.tuxungo.android.engine.EngineAssets
import com.tuxungo.android.engine.EngineRunner
import com.tuxungo.android.engine.ModelStore
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Stone

private enum class GuessPhase { GUESSING, THINKING, REVEALED }

/**
 * "Guess the top move" training: an AI-vs-AI game advances one move at
 * a time, and before each move the player guesses KataGo's first
 * choice. Guessing the top move scores 3, a top-3 move scores 1.
 * Pure training — nothing here touches the saved-game store.
 */
@Composable
fun GuessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }

    var boardSize by remember { mutableIntStateOf(19) }
    var game by remember { mutableStateOf(GoGame(BoardSize(19, 19), 7.5)) }
    var version by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(GuessPhase.GUESSING) }
    var pending by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var guessPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var feedback by remember { mutableStateOf("") }
    var score by remember { mutableIntStateOf(0) }
    var topHits by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    // All-time bests, kept across sessions.
    val guessPrefs = remember {
        context.getSharedPreferences("guess_prefs", android.content.Context.MODE_PRIVATE)
    }
    var savedBestScore by remember { mutableIntStateOf(guessPrefs.getInt("bestScore", 0)) }
    var savedBestStreak by remember { mutableIntStateOf(guessPrefs.getInt("bestStreak", 0)) }

    val runner = remember { EngineRunner() }
    var engine by remember { mutableStateOf<AnalysisEngine?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runner.shutdown()
            val eng = engine
            if (eng != null) Thread { eng.quit() }.start()
        }
    }

    fun restart(size: Int) {
        boardSize = size
        game = GoGame(BoardSize(size, size), 7.5)
        version++
        phase = GuessPhase.GUESSING
        pending = null
        result = null
        guessPoint = null
        feedback = ""
        score = 0
        topHits = 0
        answered = 0
        streak = 0
        bestStreak = 0
    }

    fun reveal(guess: Pair<Int, Int>?, isPass: Boolean, skipped: Boolean) {
        if (phase != GuessPhase.GUESSING) return
        phase = GuessPhase.THINKING
        pending = null
        guessPoint = guess
        feedback = ""
        val movesSnapshot = game.moves.toList()
        val sideSnapshot = game.current
        runner.submit({
            var eng = engine
            if (eng == null || !eng.isAlive) {
                eng = AnalysisEngine.start(
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    workDir = EngineAssets.engineWorkDir(context),
                    modelFile = ModelStore.resolveActiveFile(context)
                )
                engine = eng
            }
            eng.analyze(
                size = game.size,
                komi = game.komi,
                initialStones = emptyList(),
                initialPlayer = Stone.BLACK,
                moves = movesSnapshot,
                sideToMove = sideSnapshot,
                maxVisits = 300
            )
        }) { outcome ->
            outcome.onSuccess { res ->
                result = res
                phase = GuessPhase.REVEALED
                val top = res.candidates.firstOrNull()
                val topText = when {
                    top == null -> "无候选"
                    top.move == null -> "停一手"
                    else -> top.vertex
                }
                val wrText = "黑胜率 " + String.format("%.1f%%", res.rootWinrateBlack * 100)
                val guessCand = res.candidates.firstOrNull { c ->
                    if (isPass) {
                        c.move == null
                    } else {
                        c.move != null && guess != null &&
                            c.move.x == guess.first && c.move.y == guess.second
                    }
                }
                val guessWrText = guessCand?.let {
                    " · 你这步黑胜率 " + String.format("%.1f%%", it.winrateBlack * 100)
                } ?: ""
                if (skipped) {
                    streak = 0
                    answered++
                    feedback = "已跳过。AI 一选：$topText · $wrText"
                } else {
                    val rank = res.candidates.indexOfFirst { c ->
                        if (isPass) {
                            c.move == null
                        } else {
                            c.move != null && guess != null &&
                                c.move.x == guess.first && c.move.y == guess.second
                        }
                    } + 1 // 0 when not found
                    answered++
                    feedback = when {
                        rank == 1 -> {
                            score += 3
                            topHits++
                            streak++
                            if (streak > bestStreak) bestStreak = streak
                            "命中一选！+3 分 · $wrText"
                        }
                        rank in 2..3 -> {
                            score += 1
                            streak = 0
                            "你猜的是第 $rank 选，+1 分。AI 一选：$topText · $wrText$guessWrText"
                        }
                        rank in 4..5 -> {
                            streak = 0
                            "你猜的是第 $rank 选，差一点。AI 一选：$topText · $wrText$guessWrText"
                        }
                        else -> {
                            streak = 0
                            "没进前五。AI 一选：$topText · $wrText"
                        }
                    }
                }
                if (score > savedBestScore) {
                    savedBestScore = score
                    guessPrefs.edit().putInt("bestScore", score).apply()
                }
                if (bestStreak > savedBestStreak) {
                    savedBestStreak = bestStreak
                    guessPrefs.edit().putInt("bestStreak", bestStreak).apply()
                }
            }.onFailure { e ->
                phase = GuessPhase.GUESSING
                feedback = "分析失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun nextQuestion() {
        val top = result?.candidates?.firstOrNull() ?: return
        val mv = top.move
        if (mv == null) {
            game.pass()
        } else {
            game.play(mv.x, mv.y)
        }
        version++
        result = null
        guessPoint = null
        feedback = ""
        phase = GuessPhase.GUESSING
    }

    // Referenced so position changes recompose the board below.
    @Suppress("UNUSED_EXPRESSION")
    version

    val sideName = if (game.current == Stone.BLACK) "黑方" else "白方"
    val candidates = if (phase == GuessPhase.REVEALED) {
        result?.candidates?.mapIndexedNotNull { idx, c ->
            val mv = c.move ?: return@mapIndexedNotNull null
            CandidateMark(mv.x, mv.y, idx + 1)
        } ?: emptyList()
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "猜一选训练",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(
            text = "AI 自对弈逐手推进，每一手先由你猜 KataGo 的一选：猜中一选 +3 分，猜中二、三选 +1 分。",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            for (s in listOf(9, 13, 19)) {
                FilterChip(
                    selected = boardSize == s,
                    onClick = { if (boardSize != s) restart(s) },
                    label = { Text("$s 路") }
                )
            }
            OutlinedButton(onClick = { restart(boardSize) }) { Text("重新开始") }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "得分 $score · 一选命中 $topHits/$answered · 连对 $streak（最高 $bestStreak）",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
        if (savedBestScore > 0 || savedBestStreak > 0) {
            Text(
                text = "历史最佳：得分 $savedBestScore · 连对 $savedBestStreak",
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (phase) {
                GuessPhase.GUESSING ->
                    "第 ${game.moves.size + 1} 手 · 轮到$sideName：点棋盘选点，再点一次确认猜测"
                GuessPhase.THINKING -> "AI 思考中…"
                GuessPhase.REVEALED -> feedback
            },
            fontSize = 13.sp,
            color = if (phase == GuessPhase.REVEALED) Color(0xFF2F7CF6) else Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))

        BoardView(
            game = game,
            enabled = phase == GuessPhase.GUESSING,
            skin = BoardSkin.fromId(settings.boardSkin),
            showCoordinates = settings.showCoordinates,
            candidates = candidates,
            preview = if (phase == GuessPhase.REVEALED) guessPoint else pending,
            onPointTap = { x, y ->
                if (phase == GuessPhase.GUESSING && game.isLegal(x, y)) {
                    if (pending == Pair(x, y)) {
                        reveal(Pair(x, y), isPass = false, skipped = false)
                    } else {
                        pending = Pair(x, y)
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(10.dp))

        when (phase) {
            GuessPhase.GUESSING -> {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { reveal(null, isPass = true, skipped = false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("猜停一手") }
                    OutlinedButton(
                        onClick = { reveal(null, isPass = false, skipped = true) },
                        modifier = Modifier.weight(1f)
                    ) { Text("跳过这题") }
                }
            }
            GuessPhase.THINKING -> {
                Text("正在等 AI 给出候选…", fontSize = 13.sp, color = Color.Gray)
            }
            GuessPhase.REVEALED -> {
                if (game.finished) {
                    Text("这盘棋已经下完了，点「重新开始」再来一局。", fontSize = 13.sp, color = Color.Gray)
                } else {
                    Button(
                        onClick = { nextQuestion() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("落下 AI 一选，下一题") }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
