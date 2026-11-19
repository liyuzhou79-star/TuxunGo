package com.tuxungo.android.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungo.android.engine.EngineAssets
import com.tuxungo.android.engine.EngineRunner
import com.tuxungo.android.engine.KataEngine
import com.tuxungo.android.engine.ModelStore
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Sgf
import com.tuxungo.android.go.Stone
import java.io.File

@Composable
fun GameScreen(config: GameConfig, onExit: () -> Unit) {
    val context = LocalContext.current
    val game = remember(config) {
        GoGame(BoardSize(config.width, config.height), config.komi).also {
            if (config.handicap > 0) it.setupHandicap(config.handicap)
        }
    }
    var version by remember { mutableIntStateOf(0) }
    var showResignConfirm by remember { mutableStateOf(false) }
    var resultDismissed by remember { mutableStateOf(false) }

    val isPve = config.mode == GameMode.PVE
    val runner = remember { EngineRunner() }
    var engine by remember { mutableStateOf<KataEngine?>(null) }
    var engineStatus by remember { mutableStateOf(if (isPve) "引擎启动中…" else "") }
    var aiThinking by remember { mutableStateOf(false) }
    var engineFailed by remember { mutableStateOf(false) }
    var engineStarting by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<com.tuxungo.android.go.Move?>(null) }
    var suggestionIsPass by remember { mutableStateOf(false) }
    var suggesting by remember { mutableStateOf(false) }

    fun clearSuggestion() {
        suggestion = null
        suggestionIsPass = false
    }

    fun syncAndGenmove() {
        val eng = engine ?: return
        if (game.finished || game.current == config.humanStone || aiThinking) return
        aiThinking = true
        engineStatus = "AI 思考中…"
        runner.submit({
            eng.clearBoard()
            eng.setBoardSize(game.size)
            eng.setKomi(game.komi)
            if (config.handicap > 0) eng.command("fixed_handicap ${config.handicap}")
            for (move in game.moves) eng.play(move, game.size)
            eng.genmove(game.current, game.size)
        }) { result ->
            aiThinking = false
            result.onSuccess { aiMove ->
                engineStatus = ""
                if (!game.finished && game.current != config.humanStone) {
                    val applied = when {
                        aiMove == null -> game.pass()
                        else -> game.play(aiMove.x, aiMove.y) || game.pass()
                    }
                    if (applied) {
                        version++
                        resultDismissed = false
                    }
                }
            }.onFailure { e ->
                engineStatus = "引擎出错：${e.message ?: "未知错误"}"
            }
        }
    }

    fun startEngine(onReady: (Boolean) -> Unit) {
        if (engine != null) {
            onReady(true)
            return
        }
        if (engineStarting) {
            onReady(false)
            return
        }
        engineStarting = true
        engineFailed = false
        engineStatus = "引擎启动中…"
        runner.submit({
            val model = ModelStore.resolveActiveFile(context)
            KataEngine.start(
                nativeLibDir = context.applicationInfo.nativeLibraryDir,
                workDir = EngineAssets.engineWorkDir(context),
                modelFile = model,
                maxVisits = config.aiVisits
            )
        }) { result ->
            engineStarting = false
            result.onSuccess { started ->
                engine = started
                engineStatus = ""
                onReady(true)
            }.onFailure { e ->
                engineFailed = true
                engineStatus = "引擎启动失败：${e.message ?: "未知错误"}"
                onReady(false)
            }
        }
    }

    fun requestSuggestion() {
        if (game.finished || aiThinking || suggesting) return
        if (isPve && game.current != config.humanStone) return
        suggesting = true
        engineStatus = "AI 正在选点…"
        startEngine { ok ->
            val eng = engine
            if (!ok || eng == null) {
                suggesting = false
                return@startEngine
            }
            runner.submit({
                eng.clearBoard()
                eng.setBoardSize(game.size)
                eng.setKomi(game.komi)
                if (config.handicap > 0) eng.command("fixed_handicap ${config.handicap}")
                for (move in game.moves) eng.play(move, game.size)
                eng.genmove(game.current, game.size)
            }) { result ->
                suggesting = false
                result.onSuccess { move ->
                    engineStatus = ""
                    if (move == null) {
                        suggestion = null
                        suggestionIsPass = true
                    } else {
                        suggestion = move
                        suggestionIsPass = false
                    }
                }.onFailure { e ->
                    engineStatus = "选点失败：${e.message ?: "未知错误"}"
                }
            }
        }
    }

    LaunchedEffect(config) {
        if (!isPve) return@LaunchedEffect
        startEngine { ok -> if (ok) syncAndGenmove() }
    }

    DisposableEffect(Unit) {
        onDispose {
            val eng = engine
            runner.shutdown()
            if (eng != null) {
                Thread { eng.quit() }.start()
            }
        }
    }

    // Read version so Compose recomposes after each move.
    @Suppress("UNUSED_EXPRESSION") version

    val finished = game.finished
    // If the engine failed to start, never soft-lock the game: the human
    // can keep playing both sides manually.
    val humanTurn = !isPve || engineFailed || game.current == config.humanStone
    val status = when {
        finished && game.resignedBy != null ->
            "对局结束：${if (game.resignedBy == Stone.BLACK) "黑" else "白"}方认输"
        finished -> "对局结束"
        else -> "第 ${game.moves.size + 1} 手 · ${if (game.current == Stone.BLACK) "黑方" else "白方"}落子"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = status, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onExit) { Text("返回") }
        }
        Text(
            text = "黑提子 ${game.capturesByBlack} · 白提子 ${game.capturesByWhite}" +
                if (config.handicap > 0) " · 让 ${config.handicap} 子" else "",
            fontSize = 13.sp, color = Color.Gray
        )
        if (engineStatus.isNotEmpty()) {
            Text(text = engineStatus, fontSize = 13.sp, color = Color(0xFF2F7CF6))
        }
        if (engineFailed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { startEngine { ok -> if (ok && isPve) syncAndGenmove() } }) {
                    Text("重试引擎")
                }
                Text(
                    text = "引擎不可用时可先双人对下",
                    fontSize = 13.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        BoardView(
            game = game,
            enabled = !finished && humanTurn && !aiThinking,
            suggestion = suggestion?.let { it.x to it.y }
        ) { x, y ->
            if (game.play(x, y)) {
                version++
                resultDismissed = false
                clearSuggestion()
                if (isPve) syncAndGenmove()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (game.pass()) {
                        version++
                        resultDismissed = false
                        clearSuggestion()
                        if (isPve) syncAndGenmove()
                    }
                },
                enabled = !finished && humanTurn && !aiThinking,
                modifier = Modifier.weight(1f)
            ) { Text("停一手") }
            OutlinedButton(
                onClick = {
                    if (isPve && !engineFailed) {
                        if (!aiThinking) {
                            var guard = 0
                            while (game.moves.isNotEmpty() && guard < 2) {
                                if (!game.undo()) break
                                guard++
                                if (game.current == config.humanStone) break
                            }
                            version++
                            clearSuggestion()
                        }
                    } else if (game.undo()) {
                        version++
                        clearSuggestion()
                    }
                },
                enabled = game.moves.isNotEmpty() && !aiThinking,
                modifier = Modifier.weight(1f)
            ) { Text("悔棋") }
            OutlinedButton(
                onClick = { showResignConfirm = true },
                enabled = !finished,
                modifier = Modifier.weight(1f)
            ) { Text("认输") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { requestSuggestion() },
                enabled = !finished && humanTurn && !aiThinking && !suggesting,
                modifier = Modifier.weight(1f)
            ) { Text(if (suggesting) "AI 选点中…" else "AI 选点") }
            if (suggestion != null || suggestionIsPass) {
                Button(
                    onClick = {
                        val s = suggestion
                        val applied = when {
                            suggestionIsPass -> game.pass()
                            s != null -> game.play(s.x, s.y)
                            else -> false
                        }
                        if (applied) {
                            version++
                            resultDismissed = false
                            clearSuggestion()
                            if (isPve) syncAndGenmove()
                        }
                    },
                    enabled = !finished && humanTurn && !aiThinking,
                    modifier = Modifier.weight(1f)
                ) { Text("就下这步") }
            }
        }
        if (suggestion != null || suggestionIsPass) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (suggestionIsPass) {
                    "AI 一选：停一手"
                } else {
                    "AI 一选：${KataEngine.toVertex(suggestion!!, game.size)}（棋盘上的蓝色 1）"
                },
                fontSize = 13.sp, color = Color(0xFF2F7CF6)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val sgf = Sgf.export(game)
                val dir = File(context.filesDir, "sgf").apply { mkdirs() }
                val file = File(dir, "tuxungo-${System.currentTimeMillis()}.sgf")
                file.writeText(sgf)
                Toast.makeText(context, "棋谱已保存：${file.name}", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导出 SGF 棋谱") }
    }

    if (showResignConfirm) {
        val resignStone = if (isPve) config.humanStone else game.current
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text("确认认输？") },
            text = { Text("${if (resignStone == Stone.BLACK) "黑方" else "白方"}将判负。") },
            confirmButton = {
                TextButton(onClick = {
                    game.resign(resignStone)
                    showResignConfirm = false
                    version++
                    resultDismissed = false
                }) { Text("认输") }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) { Text("取消") }
            }
        )
    }

    if (finished && !resultDismissed) {
        val resultText = if (game.resignedBy != null) {
            "${if (game.resignedBy == Stone.BLACK) "白" else "黑"}方胜（对方认输）"
        } else {
            val s = game.score()
            val winner = if (s.lead > 0) "黑" else "白"
            val diff = kotlin.math.abs(s.lead)
            val diffText = if (diff % 1.0 == 0.0) diff.toInt().toString() else diff.toString()
            "${winner}方胜 $diffText 目\n" +
                "黑：棋子 ${s.blackStones} + 空 ${s.blackTerritory} = ${s.blackTotal.toInt()}\n" +
                "白：棋子 ${s.whiteStones} + 空 ${s.whiteTerritory} + 贴目 ${s.komi} = ${s.whiteTotal}"
        }
        AlertDialog(
            onDismissRequest = { resultDismissed = true },
            title = { Text("对局结果") },
            text = { Text(resultText) },
            confirmButton = {
                TextButton(onClick = onExit) { Text("返回主页") }
            },
            dismissButton = {
                TextButton(onClick = { resultDismissed = true }) { Text("查看棋盘") }
            }
        )
    }
}
