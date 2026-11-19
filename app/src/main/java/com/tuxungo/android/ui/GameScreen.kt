package com.tuxungo.android.ui

import android.media.AudioAttributes
import android.media.SoundPool
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Switch
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
import com.tuxungo.android.engine.ModelInfo
import com.tuxungo.android.engine.ModelStore
import com.tuxungo.android.engine.SideAiConfig
import com.tuxungo.android.engine.SideAiStore
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
    var showAiPanel by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

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

    // ---- GTP engines: one process per side, so Black and White can
    // run different models at different strengths in the same game ----
    val runner = remember { EngineRunner() }
    var engineBlack by remember { mutableStateOf<KataEngine?>(null) }
    var engineWhite by remember { mutableStateOf<KataEngine?>(null) }
    var failedBlack by remember { mutableStateOf(false) }
    var failedWhite by remember { mutableStateOf(false) }
    var startingBlack by remember { mutableStateOf(false) }
    var startingWhite by remember { mutableStateOf(false) }
    // The config each running engine was started with: a model change
    // forces a restart, a visits change applies live via setMaxVisits.
    var startedCfgBlack by remember { mutableStateOf<SideAiConfig?>(null) }
    var startedCfgWhite by remember { mutableStateOf<SideAiConfig?>(null) }
    var sideBlack by remember {
        mutableStateOf(
            SideAiStore.load(
                context, Stone.BLACK,
                if (isPve && config.humanStone == Stone.WHITE) config.aiVisits else 200
            )
        )
    }
    var sideWhite by remember {
        mutableStateOf(
            SideAiStore.load(
                context, Stone.WHITE,
                if (isPve && config.humanStone == Stone.BLACK) config.aiVisits else 200
            )
        )
    }
    var engineStatus by remember { mutableStateOf(if (isPve || isEve) "引擎启动中…" else "") }
    var aiThinking by remember { mutableStateOf(false) }
    var eveRunning by remember { mutableStateOf(isEve) }
    fun sideCfg(stone: Stone): SideAiConfig =
        if (stone == Stone.BLACK) sideBlack else sideWhite
    fun engineFor(stone: Stone): KataEngine? =
        if (stone == Stone.BLACK) engineBlack else engineWhite
    fun failedFor(stone: Stone): Boolean =
        if (stone == Stone.BLACK) failedBlack else failedWhite
    val anyEngineFailed = (takeoverBlack && failedBlack) || (takeoverWhite && failedWhite)
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
    val placeSoundIds = remember {
        SettingsStore.placeSoundResList(settings.placeSound).map { soundPool.load(context, it, 1) }
    }
    val captureSoundIds = remember {
        SettingsStore.captureSoundResList(settings.captureSound).map { soundPool.load(context, it, 1) }
    }

    fun playMoveSound(wasCapture: Boolean) {
        if (!settings.soundEnabled) return
        val ids = if (wasCapture) captureSoundIds else placeSoundIds
        if (ids.isEmpty()) return
        // The "real" set already varies by recording; synthesized
        // sounds get a tiny pitch jitter so repeats differ slightly.
        val rate = 0.96f + kotlin.random.Random.nextFloat() * 0.08f
        soundPool.play(ids.random(), 0.9f, 0.9f, 1, 0, rate)
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

    /** Starts (or restarts) the GTP engine for [stone] per its side config. */
    fun ensureEngine(stone: Stone) {
        val cfg = sideCfg(stone)
        val current = engineFor(stone)
        val startedCfg = if (stone == Stone.BLACK) startedCfgBlack else startedCfgWhite
        if (current != null && current.isAlive) {
            when {
                startedCfg?.modelId != cfg.modelId -> {
                    // Model changed: retire this process, start fresh below.
                    if (stone == Stone.BLACK) {
                        engineBlack = null; startedCfgBlack = null
                    } else {
                        engineWhite = null; startedCfgWhite = null
                    }
                    runner.submit({ current.quit() }) { }
                }
                startedCfg?.visits != cfg.visits -> {
                    if (stone == Stone.BLACK) startedCfgBlack = cfg else startedCfgWhite = cfg
                    runner.submit({ current.setMaxVisits(cfg.visits) }) { }
                    return
                }
                else -> return
            }
        }
        val starting = if (stone == Stone.BLACK) startingBlack else startingWhite
        if (starting) return
        if (stone == Stone.BLACK) {
            startingBlack = true; failedBlack = false
        } else {
            startingWhite = true; failedWhite = false
        }
        engineStatus = "引擎启动中…"
        runner.submit({
            val model = ModelStore.resolveFile(context, cfg.modelId)
            KataEngine.start(
                nativeLibDir = context.applicationInfo.nativeLibraryDir,
                workDir = EngineAssets.engineWorkDir(context),
                modelFile = model,
                maxVisits = cfg.visits
            )
        }) { result ->
            if (stone == Stone.BLACK) startingBlack = false else startingWhite = false
            result.onSuccess { started ->
                if (stone == Stone.BLACK) {
                    engineBlack = started; startedCfgBlack = cfg
                } else {
                    engineWhite = started; startedCfgWhite = cfg
                }
                engineStatus = ""
            }.onFailure { e ->
                if (stone == Stone.BLACK) failedBlack = true else failedWhite = true
                eveRunning = false
                engineStatus = "引擎启动失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /** (Re)starts the engines of every AI-controlled side. */
    fun startEngine() {
        if (takeoverBlack) ensureEngine(Stone.BLACK)
        if (takeoverWhite) ensureEngine(Stone.WHITE)
    }

    /** Hands one side to the AI (or back to the human) from the AI panel. */
    fun setTakeover(stone: Stone, on: Boolean) {
        if (stone == Stone.BLACK) takeoverBlack = on else takeoverWhite = on
        if (on) {
            ensureEngine(stone)
        } else {
            cancelAi()
            eveRunning = false
        }
        if (takeoverBlack && takeoverWhite) eveRunning = true
    }

    /** Applies and persists a side's model/strength choice. */
    fun setSideCfg(stone: Stone, cfg: SideAiConfig) {
        if (stone == Stone.BLACK) sideBlack = cfg else sideWhite = cfg
        SideAiStore.save(context, stone, cfg)
        if (aiControls(stone)) ensureEngine(stone)
    }

    /** Syncs the position to the GTP engine and applies its move for the side to move. */
    fun aiMoveNow() {
        val moveStone = game.current
        val eng = engineFor(moveStone) ?: return
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
                if (moveStone == Stone.BLACK) failedBlack = true else failedWhite = true
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

    // ---- Automatic light analysis: keeps the header score line live ----
    var autoResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var autoKey by remember { mutableIntStateOf(-1) }
    var autoBusy by remember { mutableStateOf(false) }

    fun runAutoAnalysis() {
        if (autoBusy || analyzing || aiThinking || game.finished || scoringMode) return
        if (game.size.width !in 2..19 || game.size.height !in 2..19) return
        autoBusy = true
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
                maxVisits = 120
            )
        }) { result ->
            autoBusy = false
            result.onSuccess { res ->
                if (game.moves.size == moveCount) {
                    autoResult = res
                    autoKey = moveCount
                }
            }
        }
    }

    LaunchedEffect(version) {
        if (bothAi && eveRunning) return@LaunchedEffect
        delay(900)
        runAutoAnalysis()
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
        if (scoringBusy || game.finished) return
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
    LaunchedEffect(
        version, engineBlack, engineWhite, eveRunning,
        takeoverBlack, takeoverWhite, failedBlack, failedWhite
    ) {
        if (game.finished) return@LaunchedEffect
        if (!aiControls(game.current) || aiThinking) return@LaunchedEffect
        if (failedFor(game.current)) return@LaunchedEffect
        if (engineFor(game.current) == null) {
            ensureEngine(game.current)
            return@LaunchedEffect
        }
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
            val engB = engineBlack
            val engW = engineWhite
            val aEng = analysisEngine
            runner.shutdown()
            analysisRunner.shutdown()
            soundPool.release()
            if (engB != null) Thread { engB.quit() }.start()
            if (engW != null) Thread { engW.quit() }.start()
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
    val humanTurn = !aiControls(game.current) || failedFor(game.current)
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

    if (fullscreen) {
        // Fullscreen spectating / playing: board as large as possible,
        // no coordinates, only analysis + judgment + exit stay.
        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = status, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { fullscreen = false }) { Text("退出全屏") }
            }
            val fsA = analysis
            val fsSel = fsA?.candidates?.getOrNull(selectedCandidate)
            val fsOwnership = when {
                scoringMode -> null
                judgmentActive -> judgment?.ownershipBlack
                showTerritory -> fsA?.ownershipBlack
                else -> null
            }
            BoardView(
                game = game,
                enabled = scoringMode || (!finished && humanTurn && !aiThinking),
                skin = BoardSkin.fromId(settings.boardSkin),
                showCoordinates = false,
                territoryThreshold = 0.5,
                candidates = if (!judgmentActive && !scoringMode) {
                    fsA?.candidates?.mapNotNull { cand ->
                        cand.move?.let { CandidateMark(it.x, it.y, fsA.candidates.indexOf(cand) + 1) }
                    } ?: emptyList()
                } else emptyList(),
                ownership = fsOwnership,
                pvGhost = if (!judgmentActive && !scoringMode) {
                    fsSel?.pvMoves?.take(7) ?: emptyList()
                } else emptyList(),
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
            val fsRes = when {
                analysis != null && analysisKey == game.moves.size -> analysis
                judgmentActive -> judgment
                autoKey == game.moves.size -> autoResult
                else -> null
            }
            Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                if (fsRes != null) {
                    Text(
                        text = "黑胜率 ${formatPercent(fsRes.rootWinrateBlack)} · " +
                            "${if (fsRes.rootScoreLeadBlack >= 0) "黑" else "白"}领先约 " +
                            "${formatNum(kotlin.math.abs(fsRes.rootScoreLeadBlack))} 目",
                        fontSize = 13.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                } else if (engineStatus.isNotEmpty()) {
                    Text(
                        text = engineStatus, fontSize = 13.sp,
                        color = Color(0xFF2F7CF6), modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { runAnalysis(false) },
                    enabled = analysisAllowed && !finished,
                    modifier = Modifier.weight(1f)
                ) { Text(if (analyzing) "分析中…" else "AI 分析") }
                Button(
                    onClick = { runJudgment() },
                    enabled = analysisAllowed && !finished,
                    modifier = Modifier.weight(1f)
                ) { Text(if (judging) "判断中…" else "形式判断") }
                OutlinedButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.weight(1f)
                ) { Text("退出全屏") }
            }
            if (analyzing || judging) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { cancelAnalysis() }) { Text("取消分析") }
                }
            }
        }
    } else {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = status, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row {
                TextButton(onClick = { fullscreen = true }) { Text("全屏") }
                TextButton(onClick = { exitGame() }) { Text("返回") }
            }
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
        // Live score line: territory (incl. dead stones) + alive stones
        // + winrate, from the freshest analysis for this exact position
        // (manual analysis, judgment, or the automatic light analysis).
        val liveRes = when {
            analysis != null && analysisKey == game.moves.size -> analysis
            judgmentActive -> judgment
            autoKey == game.moves.size -> autoResult
            else -> null
        }
        if (liveRes != null) {
            val stonesArr = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            val est = TerritoryEstimate.estimate(liveRes.ownershipBlack, stonesArr, 0.5)
            var aliveB = 0
            var aliveW = 0
            for (i in stonesArr.indices) {
                val s = stonesArr[i] ?: continue
                val v = if (i < liveRes.ownershipBlack.size) liveRes.ownershipBlack[i] else 0.0
                val dead = (s == Stone.BLACK && v < -0.5) || (s == Stone.WHITE && v > 0.5)
                if (!dead) {
                    if (s == Stone.BLACK) aliveB++ else aliveW++
                }
            }
            val blackTotal = est.blackTerritory + aliveB
            val whiteTotal = est.whiteTerritory + aliveW + game.komi
            Text(
                text = "形势：黑约 ${blackTotal} 目 · 白约 ${formatNum(whiteTotal)} 目 · 黑胜率 ${formatPercent(liveRes.rootWinrateBlack)}",
                fontSize = 12.sp, color = Color.Gray
            )
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
        if (anyEngineFailed) {
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
            territoryThreshold = 0.5,
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
        AnimatedVisibility(
            visible = judgmentActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val j = judgment ?: return@AnimatedVisibility
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
        AnimatedVisibility(
            visible = !judgmentActive && (analyzing || analysisStatus.isNotEmpty() || currentAnalysis != null),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
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
                val terr = TerritoryEstimate.estimate(currentAnalysis.ownershipBlack, stones, 0.5)
                Text(
                    text = "地盘估算（含死子）：黑约 ${terr.blackTerritory} · 白约 ${terr.whiteTerritory}",
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
                text = "AI 托管中：${when { bothAi -> "双打"; takeoverBlack -> "执黑"; else -> "执白" }}（点「AI 托管」可调模型与强度）",
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
                    enabled = !finished && (engineBlack != null || engineWhite != null),
                    modifier = Modifier.weight(1f)
                ) { Text(if (eveRunning) "暂停" else "继续") }
            }
            OutlinedButton(
                onClick = { showAiPanel = true },
                enabled = !finished,
                modifier = Modifier.weight(1f)
            ) { Text("AI 托管") }
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
                }
            }
        }
        }
    }
    }

    if (showAiPanel) {
        val models = remember { ModelStore.list(context) }
        AlertDialog(
            onDismissRequest = { showAiPanel = false },
            title = { Text("AI 托管设置") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AiSideSection(
                        stone = Stone.BLACK, cfg = sideBlack, takenOver = takeoverBlack,
                        models = models,
                        onToggle = { setTakeover(Stone.BLACK, it) },
                        onCfgChange = { setSideCfg(Stone.BLACK, it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AiSideSection(
                        stone = Stone.WHITE, cfg = sideWhite, takenOver = takeoverWhite,
                        models = models,
                        onToggle = { setTakeover(Stone.WHITE, it) },
                        onCfgChange = { setSideCfg(Stone.WHITE, it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "黑白双方可以用不同模型对打。换模型会重启该方引擎（几秒），改强度立即生效。设置会自动保存。",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiPanel = false }) { Text("关闭") }
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

/** One side's controls in the AI panel: takeover switch, model picker, strength chips. */
@Composable
private fun AiSideSection(
    stone: Stone,
    cfg: SideAiConfig,
    takenOver: Boolean,
    models: List<ModelInfo>,
    onToggle: (Boolean) -> Unit,
    onCfgChange: (SideAiConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = models.firstOrNull { it.id == cfg.modelId }?.displayName ?: "内置默认模型"
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = if (stone == Stone.BLACK) "黑方" else "白方",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "AI 托管", fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Switch(checked = takenOver, onCheckedChange = onToggle)
            }
            if (takenOver) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("模型：$currentName") }
                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        for (m in models) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(m.displayName) },
                                onClick = {
                                    onCfgChange(cfg.copy(modelId = m.id))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((v, label) in listOf(50 to "简单", 200 to "中等", 800 to "困难", 2000 to "超强")) {
                        FilterChip(
                            selected = cfg.visits == v,
                            onClick = { onCfgChange(cfg.copy(visits = v)) },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    text = "思考量：${cfg.visits}（越大越强、越慢）",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        }
    }
}
