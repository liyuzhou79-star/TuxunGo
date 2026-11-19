package com.tuxungo.android.ui

import android.media.AudioAttributes
import android.media.SoundPool
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.tuxungo.android.GameStore
import com.tuxungo.android.R
import com.tuxungo.android.SettingsStore
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

private val Rose = Color(0xFFD81B60)

@Composable
fun GameScreen(config: GameConfig, initialMoves: List<Move> = emptyList(), onExit: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }
    val game = remember(config) {
        GoGame(BoardSize(config.width, config.height), config.komi).also { g ->
            when {
                config.customStones.isNotEmpty() ->
                    g.loadPosition(config.customStones, config.startPlayer)
                config.handicap > 0 -> g.setupHandicap(config.handicap)
            }
            for (m in initialMoves) {
                if (m.isPass) g.pass() else g.play(m.x, m.y)
            }
        }
    }
    var version by remember { mutableIntStateOf(0) }
    var showResignConfirm by remember { mutableStateOf(false) }
    var resultDismissed by remember { mutableStateOf(false) }
    var showTakeoverDialog by remember { mutableStateOf(false) }

    val isPve = config.mode == GameMode.PVE
    val isEve = config.mode == GameMode.EVE

    // Which colors the AI controls. PVE: the non-human side. EVE: both.
    // PVP: none at first; the human can hand either color (or both) to
    // the AI mid-game, and custom-position games may start taken over.
    var takeoverBlack by remember {
        mutableStateOf(isEve || (isPve && config.humanStone == Stone.WHITE))
    }
    var takeoverWhite by remember {
        mutableStateOf(isEve || (isPve && config.humanStone == Stone.BLACK))
    }
    fun aiControls(stone: Stone): Boolean =
        if (stone == Stone.BLACK) takeoverBlack else takeoverWhite
    val bothAi = takeoverBlack && takeoverWhite

    // ---- GTP engine (plays moves) ----
    val runner = remember { EngineRunner() }
    var engine by remember { mutableStateOf<KataEngine?>(null) }
    var engineStatus by remember { mutableStateOf(if (isPve || isEve) "引擎启动中…" else "") }
    var aiThinking by remember { mutableStateOf(false) }
    var engineFailed by remember { mutableStateOf(false) }
    var engineStarting by remember { mutableStateOf(false) }
    var eveRunning by remember { mutableStateOf(isEve) }
    // Incremented to cancel an in-flight AI move (undo / pause / release):
    // the late engine reply is then discarded instead of applied.
    var aiToken by remember { mutableIntStateOf(0) }

    // ---- Analysis engine (JSON protocol) ----
    val analysisRunner = remember { EngineRunner() }
    var analysisEngine by remember { mutableStateOf<AnalysisEngine?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
    var analysisKey by remember { mutableIntStateOf(-1) }
    var analysisStatus by remember { mutableStateOf("") }
    var selectedCandidate by remember { mutableIntStateOf(0) }
    var showTerritory by remember { mutableStateOf(false) }
    var judgment by remember { mutableStateOf<AnalysisResult?>(null) }
    var judgmentKey by remember { mutableIntStateOf(-1) }
    var judging by remember { mutableStateOf(false) }
    // Incremented to cancel an in-flight analysis: its late result is
    // discarded and the UI is freed immediately.
    var analysisToken by remember { mutableIntStateOf(0) }
    // ---- Final scoring with engine-suggested dead stones ----
    var scoringMode by remember { mutableStateOf(false) }
    var deadSet by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var scoringBusy by remember { mutableStateOf(false) }
    var scoringNote by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    // Tap-to-confirm preview point.
    var pendingPoint by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // ---- Sounds ----
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val placeSoundId = remember { soundPool.load(context, SettingsStore.placeSoundRes(settings.placeSound), 1) }
    val captureSoundId = remember { soundPool.load(context, SettingsStore.captureSoundRes(settings.captureSound), 1) }

    fun playMoveSound(wasCapture: Boolean) {
        if (!settings.soundEnabled) return
        // Tiny random pitch jitter: real stones never sound identical.
        val rate = 0.96f + kotlin.random.Random.nextFloat() * 0.08f
        soundPool.play(if (wasCapture) captureSoundId else placeSoundId, 0.9f, 0.9f, 1, 0, rate)
    }

    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
    }

    fun vibrateMove(wasCapture: Boolean) {
        if (!settings.vibrationEnabled) return
        try {
            if (wasCapture) {
                vibrator.vibrate(
                    android.os.VibrationEffect.createWaveform(longArrayOf(0, 16, 45, 16), -1)
                )
            } else {
                vibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(16, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        } catch (_: Exception) {
        }
    }

    /** Sound + haptics for a stone that just landed. */
    fun onStonePlayed(wasCapture: Boolean) {
        playMoveSound(wasCapture)
        vibrateMove(wasCapture)
    }

    fun totalCaptures(): Int = game.capturesByBlack + game.capturesByWhite

    fun clearAnalysis() {
        analysis = null
        analysisKey = -1
        judgment = null
        judgmentKey = -1
        selectedCandidate = 0
    }

    fun cancelAi() {
        aiToken++
        if (aiThinking) {
            aiThinking = false
            engineStatus = ""
        }
    }

    fun startEngine() {
        val existing = engine
        if (existing != null && existing.isAlive) return
        if (existing != null) engine = null
        if (engineStarting) return
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
            }.onFailure { e ->
                engineFailed = true
                eveRunning = false
                engineStatus = "引擎启动失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /** Syncs the position to the GTP engine and applies its move for the side to move. */
    fun aiMoveNow() {
        val eng = engine ?: return
        if (game.finished || aiThinking) return
        val myToken = aiToken
        aiThinking = true
        engineStatus = "AI 思考中…"
        runner.submit({
            eng.clearBoard()
            eng.setBoardSize(game.size)
            eng.setKomi(game.komi)
            when {
                config.customStones.isNotEmpty() -> {
                    eng.setPosition(config.customStones, game.size)
                    for (move in game.moves) eng.play(move, game.size)
                }
                config.handicap > 0 -> {
                    eng.command("fixed_handicap ${config.handicap}")
                    for (move in game.moves) eng.play(move, game.size)
                }
                else -> for (move in game.moves) eng.play(move, game.size)
            }
            eng.genmove(game.current, game.size)
        }) { result ->
            // A cancelled move (undo / pause / takeover change) is dropped.
            if (myToken != aiToken) return@submit
            aiThinking = false
            result.onSuccess { aiMove ->
                engineStatus = ""
                if (!game.finished) {
                    val capsBefore = totalCaptures()
                    val applied = when {
                        aiMove == null -> game.pass()
                        else -> game.play(aiMove.x, aiMove.y) || game.pass()
                    }
                    if (applied) {
                        version++
                        resultDismissed = false
                        pendingPoint = null
                        onStonePlayed(totalCaptures() > capsBefore)
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

    // Base position for the analysis engine.
    val baseStones: List<Move> = when {
        config.customStones.isNotEmpty() -> config.customStones
        config.handicap > 0 -> game.handicapStones.map { (x, y) -> Move(Stone.BLACK, x, y) }
        else -> emptyList()
    }
    val basePlayer: Stone = when {
        config.customStones.isNotEmpty() -> config.startPlayer
        config.handicap > 0 -> Stone.WHITE
        else -> Stone.BLACK
    }

    fun cancelAnalysis() {
        analysisToken++
        analyzing = false
        judging = false
        scoringBusy = false
        analysisStatus = "已取消分析"
        // Retire the engine so a stuck query cannot block the next one;
        // the quit runs after the in-flight task on the same runner.
        val eng = analysisEngine
        analysisEngine = null
        if (eng != null) {
            analysisRunner.submit({ eng.quit() }) { }
        }
    }

    fun runAnalysis(deep: Boolean, visitsOverride: Int? = null, onDone: (() -> Unit)? = null) {
        if (analyzing || aiThinking) {
            onDone?.invoke()
            return
        }
        val token = ++analysisToken
        // Fresh result for this exact position: reuse instantly.
        if (!deep && visitsOverride == null && analysis != null && analysisKey == game.moves.size) {
            onDone?.invoke()
            return
        }
        analyzing = true
        analysisStatus = if (deep) "深度分析中…" else "分析中…"
        val movesSnapshot = game.moves.toList()
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
                initialStones = baseStones,
                initialPlayer = basePlayer,
                moves = movesSnapshot,
                sideToMove = sideSnapshot,
                maxVisits = visitsOverride ?: if (deep) 1200 else 300
            )
        }) { result ->
            if (token != analysisToken) return@submit // cancelled meanwhile
            analyzing = false
            result.onSuccess { res ->
                if (game.moves.size == moveCount) {
                    analysis = res
                    analysisKey = moveCount
                    selectedCandidate = 0
                    analysisStatus = ""
                    // Show the territory right away; the chip can hide it.
                    if (visitsOverride == null) showTerritory = true
                } else {
                    analysisStatus = "棋盘已变化，刚才的分析已过期，请重新分析。"
                }
            }.onFailure { e ->
                analysisStatus = "分析失败：${e.message ?: "未知错误"}"
            }
            onDone?.invoke()
        }
    }

    fun runJudgment() {
        if (judging) return
        // Reuse a fresh analysis instantly — no engine round-trip, no wait.
        if (analysis != null && analysisKey == game.moves.size) {
            judgment = analysis
            judgmentKey = game.moves.size
            return
        }
        judging = true
        runAnalysis(deep = false, visitsOverride = 400) {
            judging = false
            if (analysis != null && analysisKey == game.moves.size) {
                judgment = analysis
                judgmentKey = game.moves.size
            }
        }
    }

    /**
     * Enters final-scoring mode: the analysis engine judges dead stones
     * (600 visits), the user can tap whole groups to flip dead/alive,
     * then confirms the score. Falls back to manual marking when the
     * engine cannot judge this board.
     */
    fun enterScoring() {
        if (scoringBusy || finished) return
        cancelAi()
        scoringBusy = true
        scoringNote = "引擎判定死子中…"
        val token = ++analysisToken
        val movesSnapshot = game.moves.toList()
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
                initialStones = baseStones,
                initialPlayer = basePlayer,
                moves = movesSnapshot,
                sideToMove = sideSnapshot,
                maxVisits = 600
            )
        }) { result ->
            if (token != analysisToken) return@submit
            scoringBusy = false
            val suggested = mutableSetOf<Int>()
            result.onSuccess { res ->
                if (game.moves.size == moveCount) {
                    for (y in 0 until game.size.height) {
                        for (x in 0 until game.size.width) {
                            val st = game.stoneAt(x, y) ?: continue
                            val v = res.ownershipBlack.getOrNull(game.index(x, y)) ?: continue
                            if ((st == Stone.BLACK && v < -0.5) || (st == Stone.WHITE && v > 0.5)) {
                                suggested.add(game.index(x, y))
                            }
                        }
                    }
                }
            }
            deadSet = suggested
            scoringMode = true
            scoringNote = if (result.isSuccess) {
                "引擎已标出死子（红叉）。点某个棋块可以整块改判，确认无误后点「确认数目」。"
            } else {
                "引擎判定不可用：请自己点棋块标记死子，再确认数目。"
            }
        }
    }

    LaunchedEffect(config) {
        if (isPve || isEve) startEngine()
        // Pre-warm the analysis engine in AI modes so the first
        // analysis / judgment does not pay the engine startup cost.
        if (config.mode != GameMode.PVP) {
            analysisRunner.submit({
                if (analysisEngine == null) {
                    val model = ModelStore.resolveActiveFile(context)
                    analysisEngine = AnalysisEngine.start(
                        nativeLibDir = context.applicationInfo.nativeLibraryDir,
                        workDir = EngineAssets.engineWorkDir(context),
                        modelFile = model
                    )
                }
            }) { }
        }
    }

    // AI-vs-AI safety cap: auto-finish after 600 plies so a game
    // cannot run forever.
    LaunchedEffect(version) {
        if (bothAi && !game.finished && game.moves.size >= 600) {
            game.finishScoring()
            eveRunning = false
        }
    }

    // Drives AI turns for PVE, AI-vs-AI and mid-game takeovers alike.
    LaunchedEffect(version, engine, eveRunning, takeoverBlack, takeoverWhite, engineFailed) {
        if (engine == null || engineFailed || game.finished) return@LaunchedEffect
        if (!aiControls(game.current) || aiThinking) return@LaunchedEffect
        if (bothAi && !eveRunning) return@LaunchedEffect
        delay(if (bothAi) 650 else 300)
        if (!game.finished && aiControls(game.current) && !aiThinking) aiMoveNow()
    }

    // Persist the game on every move so backgrounding or process death
    // never loses it; MainActivity restores it on the next launch.
    LaunchedEffect(version) {
        GameStore.save(context, config, game.moves, game.finished)
    }

    DisposableEffect(Unit) {
        onDispose {
            val eng = engine
            val aEng = analysisEngine
            runner.shutdown()
            analysisRunner.shutdown()
            soundPool.release()
            if (eng != null) Thread { eng.quit() }.start()
            if (aEng != null) Thread { aEng.quit() }.start()
        }
    }

    fun exitGame() {
        GameStore.clear(context)
        onExit()
    }

    // Read version so Compose recomposes after each move.
    @Suppress("UNUSED_EXPRESSION") version

    val finished = game.finished
    // If the engine failed to start, never soft-lock the game: the human
    // can keep playing both sides manually.
    val humanTurn = !aiControls(game.current) || engineFailed
    val anyHumanSide = !takeoverBlack || !takeoverWhite
    val analysisAllowed = !analyzing && !aiThinking && !bothAi || (bothAi && !eveRunning && !analyzing && !aiThinking)
    val judgmentActive = judgment != null && judgmentKey == game.moves.size
    val status = when {
        finished && game.resignedBy != null ->
            "对局结束：${if (game.resignedBy == Stone.BLACK) "黑" else "白"}方认输"
        finished -> "对局结束"
        bothAi -> "第 ${game.moves.size + 1} 手 · AI 对 AI ${if (eveRunning) "进行中" else "已暂停"}"
        takeoverBlack || takeoverWhite ->
            "第 ${game.moves.size + 1} 手 · ${if (game.current == Stone.BLACK) "黑方" else "白方"}落子（AI 接手${if (takeoverBlack && !takeoverWhite) "黑" else if (takeoverWhite && !takeoverBlack) "白" else ""}）"
        else -> "第 ${game.moves.size + 1} 手 · ${if (game.current == Stone.BLACK) "黑方" else "白方"}落子"
    }

    fun applyHumanMove(x: Int, y: Int) {
        val capsBefore = totalCaptures()
        if (game.play(x, y)) {
            version++
            resultDismissed = false
            pendingPoint = null
            onStonePlayed(totalCaptures() > capsBefore)
            clearAnalysis()
        }
    }

    fun doUndo() {
        if (game.moves.isEmpty()) return
        cancelAi()
        if ((takeoverBlack || takeoverWhite) && !bothAi) {
            // Undo back to the last turn a human controls.
            var guard = game.moves.size + 2
            while (game.moves.isNotEmpty() && guard-- > 0) {
                if (!game.undo()) break
                if (!aiControls(game.current)) break
            }
        } else {
            game.undo()
        }
        version++
        pendingPoint = null
        clearAnalysis()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = status, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { exitGame() }) { Text("返回") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF1A1A1A), CircleShape)
                    .size(10.dp)
            )
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text(text = "提子 ${game.capturesByBlack}", fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Box(
                modifier = Modifier
                    .border(BorderStroke(1.dp, Color(0xFFB0A58F)), CircleShape)
                    .background(Color.White, CircleShape)
                    .size(10.dp)
            )
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text(text = "提子 ${game.capturesByWhite}", fontSize = 13.sp, color = Color.Gray)
            if (config.handicap > 0) {
                Text(text = " · 让 ${config.handicap} 子", fontSize = 13.sp, color = Color.Gray)
            }
            if (config.customStones.isNotEmpty()) {
                Text(text = " · 自定义布局", fontSize = 13.sp, color = Color.Gray)
            }
        }
        // Fixed-height status strip: the AI-thinking text and the
        // tap-to-confirm hint live here so the board never jumps up or
        // down when they appear and disappear.
        Box(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            val hint = when {
                pendingPoint != null -> "已选中落子点，再点一次确认落子"
                else -> engineStatus
            }
            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 13.sp,
                    color = if (pendingPoint != null) Rose else Color(0xFF2F7CF6),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (engineFailed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { startEngine() }) { Text("重试引擎") }
                Text(
                    text = "引擎不可用时可先手动对下",
                    fontSize = 13.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        val currentAnalysis = analysis
        val selected = currentAnalysis?.candidates?.getOrNull(selectedCandidate)
        val displayOwnership = when {
            scoringMode -> null
            judgmentActive -> judgment?.ownershipBlack
            showTerritory -> currentAnalysis?.ownershipBlack
            else -> null
        }
        BoardView(
            game = game,
            enabled = scoringMode || (!finished && humanTurn && !aiThinking),
            skin = BoardSkin.fromId(settings.boardSkin),
            showCoordinates = settings.showCoordinates,
            territoryThreshold = if (judgmentActive) 0.5 else 0.65,
            candidates = if (!judgmentActive && !scoringMode) {
                currentAnalysis?.candidates?.mapNotNull { cand ->
                    cand.move?.let { CandidateMark(it.x, it.y, currentAnalysis.candidates.indexOf(cand) + 1) }
                } ?: emptyList()
            } else emptyList(),
            ownership = displayOwnership,
            pvGhost = if (!judgmentActive && !scoringMode) selected?.pvMoves?.take(7) ?: emptyList() else emptyList(),
            preview = if (scoringMode) null else pendingPoint,
            markedDead = if (scoringMode) deadSet else emptySet()
        ) { x, y ->
            if (scoringMode) {
                val group = game.groupAt(x, y)
                if (group.isNotEmpty()) {
                    deadSet = if (group.all { it in deadSet }) deadSet - group else deadSet + group
                }
            } else if (!settings.confirmMove) {
                if (game.stoneAt(x, y) == null && game.isLegal(x, y)) applyHumanMove(x, y)
            } else {
                val tapped = x to y
                when {
                    pendingPoint == tapped -> applyHumanMove(x, y)
                    game.stoneAt(x, y) == null && game.isLegal(x, y) -> pendingPoint = tapped
                    else -> pendingPoint = null
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Scoring panel (dead-stone marking) ----
        if (scoringMode) {
            val s = game.scoreWithDead(deadSet)
            Text(text = "终局数目", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Rose)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = scoringNote, fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "黑：棋子 ${s.blackStones} + 空 ${s.blackTerritory} = ${formatNum(s.blackTotal)} · " +
                    "白：棋子 ${s.whiteStones} + 空 ${s.whiteTerritory} + 贴目 ${formatNum(s.komi)} = ${formatNum(s.whiteTotal)}",
                fontSize = 13.sp
            )
            Text(
                text = if (s.lead > 0) "黑胜 ${formatNum(s.lead)} 目" else "白胜 ${formatNum(-s.lead)} 目",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        game.finishScoringWith(deadSet)
                        scoringMode = false
                        version++
                        resultDismissed = false
                        clearAnalysis()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("确认数目") }
                OutlinedButton(
                    onClick = { scoringMode = false; scoringNote = "" },
                    modifier = Modifier.weight(1f)
                ) { Text("取消，继续下") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---- Judgment panel ----
        if (judgmentActive) {
            val j = judgment!!
            val stones = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            val terr = TerritoryEstimate.estimate(j.ownershipBlack, stones, 0.5)
            var blackDead = 0
            var whiteDead = 0
            for (i in stones.indices) {
                val st = stones[i] ?: continue
                val v = j.ownershipBlack.getOrNull(i) ?: continue
                if (st == Stone.WHITE && v > 0.5) whiteDead++
                if (st == Stone.BLACK && v < -0.5) blackDead++
            }
            Text(text = "形势判断", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Rose)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "黑地约 ${terr.blackTerritory} · 白地约 ${terr.whiteTerritory}" +
                    if (blackDead + whiteDead > 0) " · 死子：黑 $blackDead / 白 $whiteDead" else "",
                fontSize = 13.sp, color = Color.Gray
            )
            val lead = j.rootScoreLeadBlack
            Text(
                text = when {
                    lead > 0.5 -> "结论：黑领先约 ${formatNum(lead)} 目"
                    lead < -0.5 -> "结论：白领先约 ${formatNum(-lead)} 目"
                    else -> "结论：局势接近，难分高下"
                } + "（引擎估算，仅供参考）",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---- Analysis panel ----
        if (!judgmentActive && (analyzing || analysisStatus.isNotEmpty() || currentAnalysis != null)) {
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
                val terr = TerritoryEstimate.estimate(currentAnalysis.ownershipBlack, stones, 0.65)
                Text(
                    text = "地盘估算（较确定的空点）：黑约 ${terr.blackTerritory} · 白约 ${terr.whiteTerritory}",
                    fontSize = 13.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                currentAnalysis.candidates.forEachIndexed { idx, cand ->
                    val isSel = idx == selectedCandidate
                    Text(
                        text = "${idx + 1}${if (idx == 0) "（一选）" else ""} · ${cand.vertex} · 胜率 ${formatPercent(cand.winrateBlack)} · 访问 ${cand.visits}",
                        fontSize = 14.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Rose else Color.Unspecified,
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
                    if (selected != null && !bothAi) {
                        Button(
                            onClick = {
                                val cand = selected
                                val capsBefore = totalCaptures()
                                val applied = when {
                                    cand.move == null -> game.pass()
                                    else -> game.play(cand.move.x, cand.move.y)
                                }
                                if (applied) {
                                    version++
                                    resultDismissed = false
                                    pendingPoint = null
                                    onStonePlayed(totalCaptures() > capsBefore)
                                    clearAnalysis()
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

        if (!scoringMode) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (game.pass()) {
                        version++
                        resultDismissed = false
                        pendingPoint = null
                        clearAnalysis()
                    }
                },
                enabled = !finished && humanTurn,
                modifier = Modifier.weight(1f)
            ) { Text("停一手") }
            OutlinedButton(
                onClick = { doUndo() },
                enabled = game.moves.isNotEmpty() && (!bothAi || !eveRunning),
                modifier = Modifier.weight(1f)
            ) { Text("悔棋") }
            OutlinedButton(
                onClick = { showResignConfirm = true },
                enabled = !finished && anyHumanSide,
                modifier = Modifier.weight(1f)
            ) { Text("认输") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { runAnalysis(deep = false) },
                enabled = analysisAllowed && !finished,
                modifier = Modifier.weight(1f)
            ) { Text(if (analyzing) "分析中…" else "AI 分析") }
            OutlinedButton(
                onClick = { runJudgment() },
                enabled = !analyzing && !judging && !aiThinking && !finished,
                modifier = Modifier.weight(1f)
            ) { Text(if (judging) "判断中…" else "形式判断") }
            OutlinedButton(
                onClick = { enterScoring() },
                enabled = !finished && !scoringBusy && !aiThinking && (!bothAi || !eveRunning),
                modifier = Modifier.weight(1f)
            ) { Text(if (scoringBusy) "判定中…" else "终局数目") }
        }
        if (analyzing || judging) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { cancelAnalysis() }) { Text("取消分析") }
            }
        }
        if ((takeoverBlack || takeoverWhite) && !isPve && !isEve) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AI 接手中：${when { bothAi -> "双打"; takeoverBlack -> "执黑"; else -> "执白" }}（在「更多」里可交还）",
                fontSize = 13.sp, color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bothAi) {
                Button(
                    onClick = {
                        if (eveRunning) {
                            cancelAi()
                            eveRunning = false
                        } else {
                            eveRunning = true
                        }
                    },
                    enabled = !finished && engine != null,
                    modifier = Modifier.weight(1f)
                ) { Text(if (eveRunning) "暂停" else "继续") }
            }
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("更多") }
                androidx.compose.material3.DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("导出 SGF 棋谱") },
                        onClick = {
                            showMoreMenu = false
                            val sgf = Sgf.export(
                                game,
                                setupStones = config.customStones,
                                startPlayer = config.startPlayer
                            )
                            val dir = File(context.filesDir, "sgf").apply { mkdirs() }
                            val file = File(dir, "tuxungo-${System.currentTimeMillis()}.sgf")
                            file.writeText(sgf)
                            Toast.makeText(context, "棋谱已保存：${file.name}", Toast.LENGTH_LONG).show()
                        }
                    )
                    if (!isPve && !isEve && !finished && !(takeoverBlack || takeoverWhite)) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("AI 接手") },
                            onClick = {
                                showMoreMenu = false
                                showTakeoverDialog = true
                            }
                        )
                    }
                    if (!isPve && !isEve && (takeoverBlack || takeoverWhite)) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("交还双人") },
                            onClick = {
                                showMoreMenu = false
                                cancelAi()
                                takeoverBlack = false
                                takeoverWhite = false
                                eveRunning = false
                            }
                        )
                    }
                }
            }
        }
        }
    }

    if (showTakeoverDialog) {
        AlertDialog(
            onDismissRequest = { showTakeoverDialog = false },
            title = { Text("AI 接手") },
            text = { Text("让 AI 继续这盘棋，选一边或两边都交给 AI：") },
            confirmButton = {
                TextButton(onClick = {
                    showTakeoverDialog = false
                    takeoverBlack = true
                    takeoverWhite = true
                    eveRunning = true
                    startEngine()
                }) { Text("AI 双打") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showTakeoverDialog = false
                        takeoverBlack = true
                        startEngine()
                    }) { Text("AI 执黑") }
                    TextButton(onClick = {
                        showTakeoverDialog = false
                        takeoverWhite = true
                        startEngine()
                    }) { Text("AI 执白") }
                    TextButton(onClick = { showTakeoverDialog = false }) { Text("取消") }
                }
            }
        )
    }


    if (showResignConfirm) {
        val resignStone = when {
            bothAi -> game.current
            takeoverBlack && !takeoverWhite -> Stone.WHITE
            takeoverWhite && !takeoverBlack -> Stone.BLACK
            isPve -> config.humanStone
            else -> game.current
        }
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
            val s = game.scoringDead?.let { game.scoreWithDead(it) } ?: game.score()
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
                TextButton(onClick = { exitGame() }) { Text("返回主页") }
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
