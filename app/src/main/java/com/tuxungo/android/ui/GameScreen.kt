package com.tuxungo.android.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.tuxungo.android.engine.AnalysisEngine
import com.tuxungo.android.engine.AnalysisResult
import com.tuxungo.android.engine.EngineAssets
import com.tuxungo.android.engine.EngineRunner
import com.tuxungo.android.engine.KataEngine
import com.tuxungo.android.engine.ModelStore
import com.tuxungo.android.engine.TerritoryEstimate
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Sgf
import com.tuxungo.android.go.Stone
import java.io.File
import kotlinx.coroutines.delay

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
    var showScoreConfirm by remember { mutableStateOf(false) }
    var resultDismissed by remember { mutableStateOf(false) }

    val isPve = config.mode == GameMode.PVE
    val isEve = config.mode == GameMode.EVE

    // ---- GTP engine (plays moves) ----
    val runner = remember { EngineRunner() }
    var engine by remember { mutableStateOf<KataEngine?>(null) }
    var engineStatus by remember { mutableStateOf(if (isPve || isEve) "引擎启动中…" else "") }
    var aiThinking by remember { mutableStateOf(false) }
    var engineFailed by remember { mutableStateOf(false) }
    var engineStarting by remember { mutableStateOf(false) }
    var eveRunning by remember { mutableStateOf(false) }

    // ---- Analysis engine (JSON protocol) ----
    val analysisRunner = remember { EngineRunner() }
    var analysisEngine by remember { mutableStateOf<AnalysisEngine?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
    var analysisStatus by remember { mutableStateOf("") }
    var selectedCandidate by remember { mutableIntStateOf(0) }
    var showTerritory by remember { mutableStateOf(true) }

    fun clearAnalysis() {
        analysis = null
        selectedCandidate = 0
    }

    fun startEngine(onReady: (Boolean) -> Unit) {
        val existing = engine
        if (existing != null && existing.isAlive) {
            onReady(true)
            return
        }
        if (existing != null) engine = null
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

    /** Syncs the position to the GTP engine and applies its move for the side to move. */
    fun aiMoveNow() {
        val eng = engine ?: return
        if (game.finished || aiThinking) return
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
                if (!game.finished) {
                    val applied = when {
                        aiMove == null -> game.pass()
                        else -> game.play(aiMove.x, aiMove.y) || game.pass()
                    }
                    if (applied) {
                        version++
                        resultDismissed = false
                        clearAnalysis()
                    }
                }
            }.onFailure { e ->
                // Engine died mid-game: fall back to manual play instead
                // of leaving the human locked out on the AI's turn.
                engineFailed = true
                eveRunning = false
                engineStatus = "引擎出错：${e.message ?: "未知错误"}"
            }
        }
    }

    fun requestAiTurn() {
        if (!isPve || game.finished) return
        if (game.current == config.humanStone) return
        aiMoveNow()
    }

    fun runAnalysis(deep: Boolean) {
        if (analyzing || aiThinking) return
        analyzing = true
        analysisStatus = if (deep) "深度分析中…" else "分析中…"
        val movesSnapshot = game.moves.toList()
        val handicapSnapshot = game.handicapStones
        val sideSnapshot = game.current
        val moveCount = movesSnapshot.size
        analysisRunner.submit({
            var eng = analysisEngine
            if (eng == null || !eng.isAlive) {
                val model = ModelStore.resolveActiveFile(context)
                eng = AnalysisEngine.start(
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    workDir = EngineAssets.engineWorkDir(context),
                    modelFile = model
                )
                analysisEngine = eng
            }
            eng.analyze(
                size = game.size,
                komi = game.komi,
                handicapStones = handicapSnapshot,
                moves = movesSnapshot,
                sideToMove = sideSnapshot,
                maxVisits = if (deep) 1200 else 300
            )
        }) { result ->
            analyzing = false
            result.onSuccess { res ->
                if (game.moves.size == moveCount) {
                    analysis = res
                    selectedCandidate = 0
                    analysisStatus = ""
                } else {
                    analysisStatus = "棋盘已变化，刚才的分析已过期，请重新分析。"
                }
            }.onFailure { e ->
                analysisStatus = "分析失败：${e.message ?: "未知错误"}"
            }
        }
    }

    LaunchedEffect(config) {
        if (isPve || isEve) {
            startEngine { ok ->
                if (ok && isPve) requestAiTurn()
                if (ok && isEve) eveRunning = true
            }
        }
    }

    LaunchedEffect(eveRunning, version, engine) {
        if (isEve && eveRunning && engine != null && !game.finished && !aiThinking) {
            delay(600)
            if (eveRunning && !game.finished) aiMoveNow()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val eng = engine
            val aEng = analysisEngine
            runner.shutdown()
            analysisRunner.shutdown()
            if (eng != null) Thread { eng.quit() }.start()
            if (aEng != null) Thread { aEng.quit() }.start()
        }
    }

    // Read version so Compose recomposes after each move.
    @Suppress("UNUSED_EXPRESSION") version

    val finished = game.finished
    // If the engine failed to start, never soft-lock the game: the human
    // can keep playing both sides manually.
    val humanTurn = when {
        isEve -> false
        !isPve -> true
        else -> engineFailed || game.current == config.humanStone
    }
    val analysisAllowed = !analyzing && !aiThinking &&
        (!isPve || humanTurn) && (!isEve || !eveRunning)
    val status = when {
        finished && game.resignedBy != null ->
            "对局结束：${if (game.resignedBy == Stone.BLACK) "黑" else "白"}方认输"
        finished -> "对局结束"
        isEve -> "第 ${game.moves.size + 1} 手 · AI 对 AI ${if (eveRunning) "进行中" else "已暂停"}"
        else -> "第 ${game.moves.size + 1} 手 · ${if (game.current == Stone.BLACK) "黑方" else "白方"}落子"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                TextButton(onClick = {
                    startEngine { ok ->
                        if (ok && isPve) requestAiTurn()
                        if (ok && isEve) eveRunning = true
                    }
                }) { Text("重试引擎") }
                Text(
                    text = "引擎不可用时可先双人对下",
                    fontSize = 13.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val currentAnalysis = analysis
        val selected = currentAnalysis?.candidates?.getOrNull(selectedCandidate)
        BoardView(
            game = game,
            enabled = !finished && humanTurn && !aiThinking,
            candidates = currentAnalysis?.candidates?.mapNotNull { cand ->
                cand.move?.let { CandidateMark(it.x, it.y, currentAnalysis.candidates.indexOf(cand) + 1) }
            } ?: emptyList(),
            ownership = if (showTerritory) currentAnalysis?.ownershipBlack else null,
            pvGhost = selected?.pvMoves?.take(7) ?: emptyList()
        ) { x, y ->
            if (game.play(x, y)) {
                version++
                resultDismissed = false
                clearAnalysis()
                if (isPve) requestAiTurn()
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Analysis panel ----
        if (analyzing || analysisStatus.isNotEmpty() || currentAnalysis != null) {
            if (analyzing || (currentAnalysis == null && analysisStatus.isNotEmpty())) {
                Text(text = analysisStatus, fontSize = 13.sp, color = Color(0xFF2F7CF6))
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (currentAnalysis != null) {
                val wr = currentAnalysis.rootWinrateBlack
                Row(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(wr.coerceIn(0.02, 0.98).toFloat())
                            .height(14.dp)
                            .background(Color(0xFF1A1A1A))
                    )
                    Box(
                        modifier = Modifier
                            .weight((1.0 - wr).coerceIn(0.02, 0.98).toFloat())
                            .height(14.dp)
                            .background(Color(0xFFE8E8E8))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val lead = currentAnalysis.rootScoreLeadBlack
                Text(
                    text = "黑胜率 ${formatPercent(wr)} · " +
                        if (lead >= 0) "黑领先约 ${formatNum(lead)} 目" else "白领先约 ${formatNum(-lead)} 目",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                val stones = Array(game.size.area) { i ->
                    game.stoneAt(i % game.size.width, i / game.size.width)
                }
                val terr = TerritoryEstimate.estimate(currentAnalysis.ownershipBlack, stones)
                Text(
                    text = "地盘估算（只算空点）：黑约 ${terr.blackTerritory} · 白约 ${terr.whiteTerritory} · 未定 ${terr.neutral}",
                    fontSize = 13.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                currentAnalysis.candidates.forEachIndexed { idx, cand ->
                    val isSel = idx == selectedCandidate
                    Text(
                        text = "${idx + 1} · ${cand.vertex} · 胜率 ${formatPercent(cand.winrateBlack)} · 访问 ${cand.visits}",
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color(0xFF2F7CF6) else Color.Unspecified,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCandidate = idx }
                            .padding(vertical = 3.dp)
                    )
                }
                if (selected != null && selected.pvText.isNotEmpty()) {
                    Text(
                        text = "变化：${selected.pvText}（棋盘上半透明子为变化顺序）",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showTerritory,
                        onClick = { showTerritory = !showTerritory },
                        label = { Text("地盘显示") }
                    )
                    OutlinedButton(onClick = { runAnalysis(deep = true) }, enabled = !analyzing) {
                        Text("深度分析")
                    }
                    if (selected != null && !isEve) {
                        Button(
                            onClick = {
                                val cand = selected
                                val applied = when {
                                    cand.move == null -> game.pass()
                                    else -> game.play(cand.move.x, cand.move.y)
                                }
                                if (applied) {
                                    version++
                                    resultDismissed = false
                                    clearAnalysis()
                                    if (isPve) requestAiTurn()
                                }
                            },
                            enabled = !finished && humanTurn && !aiThinking
                        ) { Text("就下这步") }
                    }
                }
                if (analysisStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = analysisStatus, fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (game.pass()) {
                        version++
                        resultDismissed = false
                        clearAnalysis()
                        if (isPve) requestAiTurn()
                    }
                },
                enabled = !finished && humanTurn && !aiThinking,
                modifier = Modifier.weight(1f)
            ) { Text("停一手") }
            OutlinedButton(
                onClick = {
                    if (isPve && !engineFailed && !isEve) {
                        if (!aiThinking) {
                            var guard = 0
                            while (game.moves.isNotEmpty() && guard < 2) {
                                if (!game.undo()) break
                                guard++
                                if (game.current == config.humanStone) break
                            }
                            version++
                            clearAnalysis()
                        }
                    } else if (game.undo()) {
                        version++
                        clearAnalysis()
                    }
                },
                enabled = game.moves.isNotEmpty() && !aiThinking && (!isEve || !eveRunning),
                modifier = Modifier.weight(1f)
            ) { Text("悔棋") }
            OutlinedButton(
                onClick = { showResignConfirm = true },
                enabled = !finished && !isEve,
                modifier = Modifier.weight(1f)
            ) { Text("认输") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { runAnalysis(deep = false) },
                enabled = analysisAllowed,
                modifier = Modifier.weight(1f)
            ) { Text(if (analyzing) "分析中…" else "AI 分析") }
            OutlinedButton(
                onClick = { showScoreConfirm = true },
                enabled = !finished && !aiThinking && (!isEve || !eveRunning),
                modifier = Modifier.weight(1f)
            ) { Text("终局数目") }
        }
        if (isEve) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { eveRunning = !eveRunning },
                enabled = !finished && engine != null && !aiThinking,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (eveRunning) "暂停" else "继续") }
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

    if (showScoreConfirm) {
        AlertDialog(
            onDismissRequest = { showScoreConfirm = false },
            title = { Text("终局数目？") },
            text = { Text("按中国规则数目，盘上棋子都按活子计算。如有死子争议，请先协商一致再数目。") },
            confirmButton = {
                TextButton(onClick = {
                    game.finishScoring()
                    showScoreConfirm = false
                    version++
                    resultDismissed = false
                    clearAnalysis()
                }) { Text("数目") }
            },
            dismissButton = {
                TextButton(onClick = { showScoreConfirm = false }) { Text("继续下") }
            }
        )
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
            "${winner}方胜 ${formatNum(diff)} 目\n" +
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

private fun formatPercent(v: Double): String = String.format("%.1f%%", v * 100)

private fun formatNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
