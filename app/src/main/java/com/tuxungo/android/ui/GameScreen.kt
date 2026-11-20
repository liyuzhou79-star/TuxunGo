package com.tuxungo.android.ui

import android.media.AudioAttributes
import android.media.SoundPool
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.tuxungo.android.PositionShare
import com.tuxungo.android.go.Stone
import java.io.File
import kotlinx.coroutines.delay

private val Rose = Color(0xFFD81B60)

@Composable
fun GameScreen(
    config: GameConfig,
    initialMoves: List<Move> = emptyList(),
    onExit: () -> Unit,
    onReview: (com.tuxungo.android.LibraryGame) -> Unit = {},
    onReviewAtPly: (com.tuxungo.android.LibraryGame, Int) -> Unit = { g, _ -> onReview(g) },
    onRestart: () -> Unit = {},
    initialStats: Map<Int, com.tuxungo.android.engine.TurnStat> = emptyMap(),
    initialClock: GameStore.ClockSnapshot? = null,
    initialTimeoutBy: Stone? = null
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }
    // Optional keep-awake while a game is on screen.
    DisposableEffect(settings.keepScreenOn) {
        val activity = context as? android.app.Activity
        if (settings.keepScreenOn) {
            activity?.window?.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        onDispose {
            activity?.window?.clearFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
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
            if (initialTimeoutBy != null) g.timeout(initialTimeoutBy)
        }
    }
    var version by remember { mutableIntStateOf(0) }
    var showResignConfirm by remember { mutableStateOf(false) }
    var resultDismissed by remember { mutableStateOf(false) }
    var showAiPanel by remember { mutableStateOf(false) }
    var showStrongDownload by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(config.focusMode) }
    var showTrueColors by remember { mutableStateOf(false) }
    var lastTickSecond by remember { mutableIntStateOf(-1) }

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
    // Move count whose AI engine already used its one automatic
    // restart; a second failure on the same move falls back to
    // manual play instead of looping forever.
    var recoveryUsedForMove by remember { mutableIntStateOf(-1) }
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
    // Manual pause for timed games: freezes the clock and the board.
    var userPaused by remember { mutableStateOf(false) }
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
    var continuousAnalysis by remember { mutableStateOf(false) }
    var analysisExpanded by remember { mutableStateOf(false) }
    var judgmentKey by remember { mutableIntStateOf(-1) }
    var judging by remember { mutableStateOf(false) }
    // ---- AI resign proposals + the smart referee ----
    var resignProposal by remember { mutableStateOf<Stone?>(null) }
    var resignRefusedAtBlack by remember { mutableIntStateOf(-100) }
    var resignRefusedAtWhite by remember { mutableIntStateOf(-100) }
    var refereeEnabled by remember { mutableStateOf(settings.refereeEnabled) }
    var settledStreak by remember { mutableIntStateOf(0) }
    var refereeNotice by remember {
        mutableStateOf<Pair<String, Set<Int>>?>(null)
    }
    var refereeSnoozeUntil by remember { mutableIntStateOf(-1) }
    // Incremented to cancel an in-flight analysis: its late result is
    // discarded and the UI is freed immediately.
    var analysisToken by remember { mutableIntStateOf(0) }
    // ---- Final scoring with engine-suggested dead stones ----
    var scoringMode by remember { mutableStateOf(false) }
    var deadSet by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var scoringBusy by remember { mutableStateOf(false) }
    var scoringNote by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var boardFlipped by remember { mutableStateOf(false) }
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
                startedCfg?.modelId != cfg.modelId ||
                    startedCfg?.humanLike != cfg.humanLike ||
                    startedCfg?.rank != cfg.rank -> {
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
                    val effective = if (SideAiStore.rankProfile(cfg.rank) != null) {
                        minOf(cfg.visits, 60)
                    } else {
                        cfg.visits
                    }
                    runner.submit({ current.setMaxVisits(effective) }) { }
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
            // The human model file backs both human-like play and
            // rank capping; if it was deleted, both quietly disable.
            val humanFile = if (cfg.humanLike || cfg.rank != "max") {
                ModelStore.humanModelFile(context)
            } else {
                null
            }
            val rankProfile = SideAiStore.rankProfile(cfg.rank)
                ?.takeIf { humanFile != null }
            KataEngine.start(
                nativeLibDir = context.applicationInfo.nativeLibraryDir,
                workDir = EngineAssets.engineWorkDir(context),
                modelFile = model,
                // A ranked AI imitates the human policy directly;
                // a small search budget is plenty and keeps it fast.
                maxVisits = if (rankProfile != null) {
                    minOf(cfg.visits, 60)
                } else {
                    cfg.visits
                },
                humanModelFile = humanFile,
                rankProfile = rankProfile
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
        if (resignProposal != null) return
        // AI resign proposal: at full strength, when the freshest
        // judgment already calls this side hopeless (win rate at
        // most 2% and 12+ points behind, 40+ moves in), the AI asks
        // the user before resigning — it never just quits. After a
        // refusal it must wait at least 15 moves to ask again.
        run {
            val refusedAt = if (moveStone == Stone.BLACK) {
                resignRefusedAtBlack
            } else {
                resignRefusedAtWhite
            }
            if (sideCfg(moveStone).rank == "max" &&
                game.moves.size >= 40 &&
                game.moves.size - refusedAt >= 15
            ) {
                val res = when {
                    autoKey == game.moves.size -> autoResult
                    analysisKey == game.moves.size -> analysis
                    else -> null
                }
                val wrSide = res?.let {
                    if (moveStone == Stone.BLACK) {
                        it.rootWinrateBlack
                    } else {
                        1.0 - it.rootWinrateBlack
                    }
                }
                if (res != null && wrSide != null && wrSide <= 0.02 &&
                    kotlin.math.abs(res.rootScoreLeadBlack) >= 12.0
                ) {
                    resignProposal = moveStone
                    return
                }
            }
        }
        val myToken = aiToken
        aiThinking = true
        engineStatus = "AI 思考中…"
        var genMs = 0L
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
            val t0 = System.nanoTime()
            val mv = eng.genmove(game.current, game.size)
            genMs = (System.nanoTime() - t0) / 1_000_000
            mv
        }) { result ->
            // A cancelled move (undo / pause / takeover change) is dropped.
            if (myToken != aiToken) return@submit
            aiThinking = false
            result.onSuccess { aiMove ->
                engineStatus = if (genMs > 0) {
                    "AI 用时 " + if (genMs < 1000) {
                        "$genMs 毫秒"
                    } else {
                        String.format("%.1f 秒", genMs / 1000.0)
                    }
                } else {
                    ""
                }
                if (!game.finished) {
                    val capsBefore = totalCaptures()
                    val refusedAt = if (moveStone == Stone.BLACK) {
                        resignRefusedAtBlack
                    } else {
                        resignRefusedAtWhite
                    }
                    val applied = when {
                        // The engine itself answered "resign": turn
                        // it into a proposal the user consents to
                        // (same 15-move refusal cooldown), never an
                        // instant loss.
                        aiMove == null && eng.lastGenmoveResigned &&
                            sideCfg(moveStone).rank == "max" &&
                            game.moves.size - refusedAt >= 15 -> {
                            resignProposal = moveStone
                            false
                        }
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
                if (recoveryUsedForMove != game.moves.size) {
                    // First failure on this move: the engine process
                    // likely died. Drop it, let the driver effect
                    // restart it and retry the move automatically.
                    recoveryUsedForMove = game.moves.size
                    runner.submit({ runCatching { eng.quit() } }) { }
                    if (moveStone == Stone.BLACK) {
                        engineBlack = null
                        startedCfgBlack = null
                    } else {
                        engineWhite = null
                        startedCfgWhite = null
                    }
                    engineStatus = "引擎异常，正在自动重启…"
                } else {
                    // Second failure on the same move: fall back to
                    // manual play instead of leaving the human locked
                    // out on the AI's turn.
                    if (moveStone == Stone.BLACK) failedBlack = true else failedWhite = true
                    eveRunning = false
                    engineStatus = "引擎出错：${e.message ?: "未知错误"}"
                }
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
        // Retire the engine RIGHT NOW (destroy is non-blocking): the
        // in-flight analyze() notices the dead process within half a
        // second and unwinds, freeing the runner. Queueing the quit
        // behind the stuck task was the old behavior — the quit then
        // never ran and everything stayed "analyzing" forever.
        val eng = analysisEngine
        analysisEngine = null
        eng?.quit()
    }

    // Per-turn win-rate stats recorded quietly while the game runs, so
    // the post-game review opens (nearly) instantly instead of analyzing
    // the whole game from scratch. Nothing in the UI reads this during
    // play; it only rides along into the review / library record.
    val liveStats = remember {
        androidx.compose.runtime.mutableStateMapOf<Int, com.tuxungo.android.engine.TurnStat>()
            .apply { putAll(initialStats) }
    }

    fun recordTurnStat(moveCount: Int, res: AnalysisResult, overwrite: Boolean) {
        val first = res.candidates.firstOrNull()
        val second = res.candidates.getOrNull(1)
        val stat = com.tuxungo.android.engine.TurnStat(
            turn = moveCount,
            winrateBlack = res.rootWinrateBlack,
            scoreLeadBlack = res.rootScoreLeadBlack,
            bestMove = first?.move,
            bestIsPass = first != null && first.move == null,
            bestGap = if (first != null && second != null) {
                kotlin.math.abs(first.winrateBlack - second.winrateBlack)
            } else {
                0.0
            },
            bestPv = first?.let { c ->
                if (c.pvText.isNotBlank()) {
                    c.pvText
                } else {
                    c.pvMoves.joinToString(" ") { m ->
                        KataEngine.toVertex(m, game.size)
                    }
                }
            } ?: ""
        )
        if (overwrite || !liveStats.containsKey(moveCount)) {
            liveStats[moveCount] = stat
        }
    }

    fun runAnalysis(deep: Boolean, visitsOverride: Int? = null, onDone: (() -> Unit)? = null) {
        if (analyzing || aiThinking) {
            onDone?.invoke()
            return
        }
        // Only cancelAnalysis() may invalidate a token. Starting one
        // task must never silently kill another task's callback (that
        // cross-invalidation left "analyzing" stuck on forever).
        val token = analysisToken
        // Fresh result for this exact position: reuse instantly.
        if (!deep && visitsOverride == null && analysis != null && analysisKey == game.moves.size) {
            onDone?.invoke()
            return
        }
        analyzing = true
        analysisStatus = when {
            deep -> "深度分析中…"
            visitsOverride != null && visitsOverride <= 150 -> "快速分析中…"
            else -> "分析中…"
        }
        val movesSnapshot = game.moves.toList()
        val sideSnapshot = game.current
        val moveCount = movesSnapshot.size
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        analysisRunner.submit({
            var eng = analysisEngine
            if (eng == null || !eng.isAlive) {
                val model = ModelStore.resolveActiveFile(context)
                eng = AnalysisEngine.start(
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    workDir = EngineAssets.engineWorkDir(context),
                    modelFile = model,
                    humanModelFile = ModelStore.humanModelFile(context)
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
                maxVisits = visitsOverride ?: if (deep) 1200 else 300,
                onProvisional = { prov ->
                    // Show the provisional top move as soon as the
                    // search has one, then let the final result
                    // refine it (the panel keeps its "analyzing"
                    // state until the final callback lands).
                    mainHandler.post {
                        if (token == analysisToken && game.moves.size == moveCount) {
                            analysis = prov
                            analysisKey = moveCount
                            selectedCandidate = 0
                        }
                    }
                }
            )
        }) { result ->
            if (token != analysisToken) {
                // Cancelled meanwhile; cancel already reset the flags,
                // but never leave this one dangling either way.
                analyzing = false
                return@submit
            }
            analyzing = false
            result.onSuccess { res ->
                if (game.moves.size == moveCount) {
                    analysis = res
                    analysisKey = moveCount
                    selectedCandidate = 0
                    analysisStatus = ""
                    // Manual analysis is deeper than the automatic one:
                    // let it upgrade this turn's recorded review stat.
                    recordTurnStat(moveCount, res, overwrite = true)
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
                    modelFile = model,
                    humanModelFile = ModelStore.humanModelFile(context)
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
                maxVisits = settings.autoVisits
            )
        }) { result ->
            autoBusy = false
            result.onSuccess { res ->
                // Record the stat for this turn even if the game has
                // already moved on — it is historical review data.
                recordTurnStat(moveCount, res, overwrite = false)
                if (game.moves.size == moveCount) {
                    autoResult = res
                    autoKey = moveCount
                }
            }
        }
    }

    LaunchedEffect(version) {
        // In AI-vs-AI the recorder keeps up between moves; in human games
        // it waits for the AI to finish thinking so the AI-to-move turns
        // get recorded too. The analysis engine is a separate process,
        // and nothing here touches the board or the move flow.
        var waited = 0
        while (aiThinking && !game.finished && waited < 40) {
            delay(300)
            waited++
        }
        if (waited >= 40) return@LaunchedEffect
        delay(if (bothAi && eveRunning) 200 else 900)
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
        // The background auto-analysis judges every position as the
        // game goes; when its result is for this exact position,
        // judgment is instant too. (Judgment used to ignore it and
        // always pay a full high-visit engine pass — with the strong
        // model that pass could run past the timeout and the panel
        // simply never appeared.)
        if (autoResult != null && autoKey == game.moves.size) {
            judgment = autoResult
            judgmentKey = game.moves.size
            return
        }
        if (analyzing) return
        // Judgment runs its own engine pass and never touches the
        // manual analysis state — opening judgment used to light up
        // the "close analysis" button next to it, which confused
        // users. Provisional results stream into the panel as they
        // firm up, so even a slow full pass shows territory early.
        judging = true
        val token = analysisToken
        val movesSnapshot = game.moves.toList()
        val sideSnapshot = game.current
        val moveCount = movesSnapshot.size
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        analysisRunner.submit({
            var eng = analysisEngine
            if (eng == null || !eng.isAlive) {
                val model = ModelStore.resolveActiveFile(context)
                eng = AnalysisEngine.start(
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    workDir = EngineAssets.engineWorkDir(context),
                    modelFile = model,
                    humanModelFile = ModelStore.humanModelFile(context)
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
                maxVisits = settings.judgeVisits,
                onProvisional = { prov ->
                    mainHandler.post {
                        if (token == analysisToken && game.moves.size == moveCount) {
                            judgment = prov
                            judgmentKey = moveCount
                        }
                    }
                }
            )
        }) { result ->
            judging = false
            if (token != analysisToken) return@submit
            result.onSuccess { res ->
                if (game.moves.size == moveCount) {
                    judgment = res
                    judgmentKey = moveCount
                }
            }
        }
    }

    /** Applies the referee's verdict: score with its dead stones. */
    fun finishReferee() {
        val notice = refereeNotice ?: return
        refereeNotice = null
        game.finishScoringWith(notice.second)
        version++
        resultDismissed = false
    }

    // Smart referee: while enabled, every fresh background result
    // is checked for a fully settled game — nearly every empty
    // point's ownership is decided AND the engine's own top choice
    // is to pass. Two settled checks in a row raise the verdict;
    // the dialog then counts down 3 seconds before it is applied,
    // and the user can wave it off (snoozed for 6 moves).
    LaunchedEffect(autoKey, version, refereeEnabled) {
        if (!refereeEnabled || game.finished || scoringMode ||
            resignProposal != null || game.moves.size < 20 ||
            game.moves.size < refereeSnoozeUntil
        ) {
            settledStreak = 0
            return@LaunchedEffect
        }
        val res = autoResult
        if (res == null || autoKey != game.moves.size) {
            settledStreak = 0
            return@LaunchedEffect
        }
        val own = res.ownershipBlack
        if (own.size != game.size.area) {
            settledStreak = 0
            return@LaunchedEffect
        }
        var emptyCount = 0
        var settledCount = 0
        for (i in own.indices) {
            val x = i % game.size.width
            val y = i / game.size.width
            if (game.stoneAt(x, y) == null) {
                emptyCount++
                if (kotlin.math.abs(own[i]) >= 0.8) settledCount++
            }
        }
        val settledShare = if (emptyCount == 0) {
            1.0
        } else {
            settledCount.toDouble() / emptyCount
        }
        val topIsPass = res.candidates.firstOrNull()?.let {
            it.move == null
        } == true
        settledStreak = if (settledShare >= 0.97 && topIsPass) {
            settledStreak + 1
        } else {
            0
        }
        if (settledStreak >= 2 && refereeNotice == null) {
            val stonesNow = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            val dead = TerritoryEstimate.deadStones(
                own, stonesNow, game.size.width, game.size.height,
                SettingsStore.deadGroupMean(settings.deadStrictness)
            )
            val s = game.scoreWithDead(dead)
            val winner = if (s.lead > 0) "黑" else "白"
            refereeNotice =
                "${winner}方胜 ${formatNum(kotlin.math.abs(s.lead))} 目" to dead
        }
    }

    // Continuous analysis: once switched on in the analysis
    // panel, every new position is analyzed automatically so
    // the user can keep playing with guidance, no re-tapping.
    LaunchedEffect(version, aiThinking, continuousAnalysis, focusMode) {
        if (!continuousAnalysis || focusMode || game.finished) {
            return@LaunchedEffect
        }
        if (bothAi && eveRunning) return@LaunchedEffect
        if (analysisKey == game.moves.size) return@LaunchedEffect
        delay(600)
        if (!analyzing && !aiThinking && !game.finished) {
            runAnalysis(deep = false)
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
        // Instant path: a fresh judgment / analysis / background
        // result for this exact position already carries the
        // ownership map scoring needs — reuse it instead of paying
        // another full engine pass at the end of the game.
        val freshRes = when {
            judgment != null && judgmentKey == game.moves.size -> judgment
            analysis != null && analysisKey == game.moves.size -> analysis
            autoResult != null && autoKey == game.moves.size -> autoResult
            else -> null
        }
        if (freshRes != null) {
            val stonesNow = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            deadSet = TerritoryEstimate.deadStones(
                freshRes.ownershipBlack, stonesNow,
                game.size.width, game.size.height,
                SettingsStore.deadGroupMean(settings.deadStrictness)
            ).toMutableSet()
            judgment = freshRes
            judgmentKey = game.moves.size
            scoringMode = true
            scoringNote = "引擎已标出死子（红叉）。点某个棋块可以整块改判，确认无误后点「确认数目」。"
            return
        }
        scoringBusy = true
        scoringNote = "引擎判定死子中…"
        val token = analysisToken
        val movesSnapshot = game.moves.toList()
        val sideSnapshot = game.current
        val moveCount = movesSnapshot.size
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        analysisRunner.submit({
            var eng = analysisEngine
            if (eng == null || !eng.isAlive) {
                val model = ModelStore.resolveActiveFile(context)
                eng = AnalysisEngine.start(
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                    workDir = EngineAssets.engineWorkDir(context),
                    modelFile = model,
                    humanModelFile = ModelStore.humanModelFile(context)
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
                // Scoring needs the ownership map to converge, not
                // full playing depth — cap it so the wait stays short.
                maxVisits = minOf(settings.judgeVisits, 500),
                onProvisional = { prov ->
                    mainHandler.post {
                        if (token == analysisToken && game.moves.size == moveCount) {
                            judgment = prov
                            judgmentKey = moveCount
                        }
                    }
                }
            )
        }) { result ->
            scoringBusy = false
            if (token != analysisToken) return@submit // cancelled meanwhile
            val suggested = mutableSetOf<Int>()
            result.onSuccess { res ->
                if (game.moves.size == moveCount) {
                    // Group-aware suggestion: whole dead groups are
                    // marked together instead of noisy per-point picks.
                    val stonesNow = Array(game.size.area) { i ->
                        game.stoneAt(i % game.size.width, i / game.size.width)
                    }
                    suggested.addAll(
                        TerritoryEstimate.deadStones(
                            res.ownershipBlack, stonesNow,
                            game.size.width, game.size.height,
                            SettingsStore.deadGroupMean(settings.deadStrictness)
                        )
                    )
                    // Scoring also turns on the position judgment:
                    // when scoring mode ends, the board keeps
                    // showing the judged territory and dead stones.
                    judgment = res
                    judgmentKey = moveCount
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
                    // Adopt the engine MainActivity pre-warmed at
                    // launch when it is still around and healthy.
                    val warmed =
                        com.tuxungo.android.engine.AnalysisEngineHolder.take()
                    if (warmed != null && warmed.isAlive) {
                        analysisEngine = warmed
                    } else {
                        val model = ModelStore.resolveActiveFile(context)
                        analysisEngine = AnalysisEngine.start(
                            nativeLibDir = context.applicationInfo.nativeLibraryDir,
                            workDir = EngineAssets.engineWorkDir(context),
                            modelFile = model,
                            humanModelFile = ModelStore.humanModelFile(context)
                        )
                    }
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
        takeoverBlack, takeoverWhite, failedBlack, failedWhite,
        resignProposal
    ) {
        if (game.finished) return@LaunchedEffect
        if (resignProposal != null) return@LaunchedEffect
        if (!aiControls(game.current) || aiThinking) return@LaunchedEffect
        if (failedFor(game.current)) return@LaunchedEffect
        if (engineFor(game.current) == null) {
            ensureEngine(game.current)
            return@LaunchedEffect
        }
        if (bothAi && !eveRunning) return@LaunchedEffect
        val eveDelayMs = when (settings.eveSpeed) {
            "fast" -> 300L
            "slow" -> 1200L
            else -> 650L
        }
        delay(if (bothAi) eveDelayMs else 300)
        if (!game.finished && aiControls(game.current) && !aiThinking) aiMoveNow()
    }

    // ---- Game clock (optional): main time per side, then byoyomi
    // 3 x 30 s. Starts on the first move; pauses with a paused AI game;
    // running out of everything loses on time. ----
    val clockEnabled = config.clockMinutes > 0
    var clockBlackMainMs by remember {
        mutableLongStateOf(initialClock?.blackMainMs ?: config.clockMinutes * 60_000L)
    }
    var clockWhiteMainMs by remember {
        mutableLongStateOf(initialClock?.whiteMainMs ?: config.clockMinutes * 60_000L)
    }
    var clockBlackPeriods by remember {
        mutableIntStateOf(initialClock?.blackPeriods ?: config.byoyomiPeriods)
    }
    var clockWhitePeriods by remember {
        mutableIntStateOf(initialClock?.whitePeriods ?: config.byoyomiPeriods)
    }
    var clockBlackPeriodMs by remember {
        mutableLongStateOf(initialClock?.blackPeriodMs ?: 30_000L)
    }
    var clockWhitePeriodMs by remember {
        mutableLongStateOf(initialClock?.whitePeriodMs ?: 30_000L)
    }

    fun clockSnapshot(): GameStore.ClockSnapshot? =
        if (!clockEnabled) {
            null
        } else {
            GameStore.ClockSnapshot(
                blackMainMs = clockBlackMainMs,
                whiteMainMs = clockWhiteMainMs,
                blackPeriods = clockBlackPeriods,
                whitePeriods = clockWhitePeriods,
                blackPeriodMs = clockBlackPeriodMs,
                whitePeriodMs = clockWhitePeriodMs
            )
        }

    LaunchedEffect(clockEnabled) {
        if (!clockEnabled) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (true) {
            delay(200)
            val now = System.currentTimeMillis()
            val dt = now - last
            last = now
            if (game.finished || scoringMode) continue
            if (game.moves.isEmpty()) continue
            if (userPaused) continue
            if (bothAi && !eveRunning) continue
            when (game.current) {
                Stone.BLACK -> {
                    if (clockBlackMainMs > 0) {
                        clockBlackMainMs = (clockBlackMainMs - dt).coerceAtLeast(0)
                    } else if (clockBlackPeriodMs > dt) {
                        clockBlackPeriodMs -= dt
                    } else if (clockBlackPeriods > 1) {
                        clockBlackPeriods -= 1
                        clockBlackPeriodMs = 30_000L
                    } else {
                        clockBlackPeriods = 0
                        clockBlackPeriodMs = 0
                        cancelAi()
                        game.timeout(Stone.BLACK)
                        version++
                    }
                }
                Stone.WHITE -> {
                    if (clockWhiteMainMs > 0) {
                        clockWhiteMainMs = (clockWhiteMainMs - dt).coerceAtLeast(0)
                    } else if (clockWhitePeriodMs > dt) {
                        clockWhitePeriodMs -= dt
                    } else if (clockWhitePeriods > 1) {
                        clockWhitePeriods -= 1
                        clockWhitePeriodMs = 30_000L
                    } else {
                        clockWhitePeriods = 0
                        clockWhitePeriodMs = 0
                        cancelAi()
                        game.timeout(Stone.WHITE)
                        version++
                    }
                }
            }
            // Byoyomi countdown: one tick per second through the
            // last 10 seconds of the side-to-move's period.
            val tickMain = if (game.current == Stone.BLACK) {
                clockBlackMainMs
            } else {
                clockWhiteMainMs
            }
            val tickPeriod = if (game.current == Stone.BLACK) {
                clockBlackPeriodMs
            } else {
                clockWhitePeriodMs
            }
            if (tickMain <= 0L && tickPeriod in 1L..10_500L) {
                val sec = (tickPeriod / 1000L).toInt()
                if (sec != lastTickSecond) {
                    lastTickSecond = sec
                    if (settings.soundEnabled && placeSoundIds.isNotEmpty()) {
                        soundPool.play(
                            placeSoundIds[0], 0.5f, 0.5f, 1, 0, 1.6f
                        )
                    }
                }
            } else if (tickPeriod > 10_500L) {
                lastTickSecond = -1
            }
        }
    }

    // Persist the game on every move so backgrounding or process death
    // never loses it; MainActivity restores it on the next launch.
    LaunchedEffect(version) {
        GameStore.save(
            context, config, game.moves, game.finished, liveStats.toMap(),
            clockSnapshot(), game.timeoutBy
        )
        // A move played in byoyomi refills that side's current period.
        val lastMove = game.moves.lastOrNull()
        if (clockEnabled && lastMove != null) {
            if (lastMove.stone == Stone.BLACK && clockBlackMainMs <= 0L) {
                clockBlackPeriodMs = 30_000L
            }
            if (lastMove.stone == Stone.WHITE && clockWhiteMainMs <= 0L) {
                clockWhitePeriodMs = 30_000L
            }
        }
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

    var showExitConfirm by remember { mutableStateOf(false) }

    /** Leaving mid-game abandons the unfinished game, so ask first. */
    fun requestExit() {
        if (game.finished || game.moves.isEmpty()) {
            exitGame()
        } else {
            showExitConfirm = true
        }
    }

    BackHandler {
        if (fullscreen) {
            fullscreen = false
        } else {
            requestExit()
        }
    }

    var showSaveToLibrary by remember { mutableStateOf(false) }
    var savedToLibrary by remember { mutableStateOf(false) }
    var showScoreDetail by remember { mutableStateOf(false) }

    fun resultSummary(): String {
        if (game.timeoutBy != null) {
            return "${if (game.timeoutBy == Stone.BLACK) "白" else "黑"}方胜（对方超时）"
        }
        if (game.resignedBy != null) {
            return "${if (game.resignedBy == Stone.BLACK) "白" else "黑"}方胜（对方认输）"
        }
        val s = game.scoringDead?.let { game.scoreWithDead(it) } ?: game.score()
        val winner = if (s.lead > 0) "黑" else "白"
        return "${winner}方胜 ${formatNum(kotlin.math.abs(s.lead))} 目"
    }

    fun buildLibraryGame(): com.tuxungo.android.LibraryGame {
        val modeLabel = when {
            config.customStones.isNotEmpty() -> "摆棋对局"
            config.mode == GameMode.EVE -> "AI 对 AI"
            config.mode == GameMode.PVE -> "人机对弈"
            else -> "双人对弈"
        }
        return com.tuxungo.android.LibraryGame(
            id = "",
            folderId = null,
            title = "$modeLabel · ${config.width}×${config.height} · ${game.moves.size} 手",
            createdAt = System.currentTimeMillis(),
            modeLabel = modeLabel,
            size = game.size,
            komi = game.komi,
            handicap = config.handicap,
            customStones = config.customStones,
            startPlayer = config.startPlayer,
            moves = game.moves.toList(),
            resultText = if (game.finished) resultSummary() else "",
            statsCache = if (liveStats.isNotEmpty()) {
                com.tuxungo.android.engine.ReviewMath.encodeSparse(liveStats)
            } else {
                null
            }
        )
    }

    // Read version so Compose recomposes after each move.
    @Suppress("UNUSED_EXPRESSION") version

    // Auto-save a game into the library's auto folder the moment it
    // finishes, so it is never lost just because nobody tapped collect.
    // Only on the in-session transition: a game restored already
    // finished was saved when it ended (or predates this feature).
    val finishedAtStart = remember { game.finished }
    var autoSaved by remember { mutableStateOf(false) }
    LaunchedEffect(game.finished) {
        if (game.finished && !finishedAtStart && !autoSaved) {
            autoSaved = true
            try {
                val folder = com.tuxungo.android.LibraryStore.ensureFolder(context, "自动保存")
                com.tuxungo.android.LibraryStore.saveGame(
                    context,
                    buildLibraryGame().copy(folderId = folder.id)
                )
            } catch (_: Exception) {
            }
        }
    }

    // AI-vs-AI series (first to N wins, colours swap each game):
    // record the result once per game, show the tally in the
    // result dialog, and auto-start the next game until the
    // series is decided. Only on the in-session transition, like
    // the auto-save above, so a restored finished game is never
    // counted twice.
    var seriesNote by remember { mutableStateOf("") }
    var seriesRecorded by remember { mutableStateOf(false) }
    LaunchedEffect(game.finished) {
        if (!game.finished || finishedAtStart || seriesRecorded) {
            return@LaunchedEffect
        }
        if (config.seriesTarget <= 0) return@LaunchedEffect
        seriesRecorded = true
        val summary = resultSummary()
        val blackWon = summary.startsWith("黑方胜")
        val whiteWon = summary.startsWith("白方胜")
        if (!blackWon && !whiteWon) return@LaunchedEffect
        val bName = ModelStore.list(context)
            .firstOrNull { it.id == sideBlack.modelId }?.displayName
            ?: "轻量模型"
        val wName = ModelStore.list(context)
            .firstOrNull { it.id == sideWhite.modelId }?.displayName
            ?: "轻量模型"
        val tally = com.tuxungo.android.SeriesStore.record(
            context, bName, wName, blackWon
        )
        val scoreLine = "系列赛（先赢 ${config.seriesTarget} 局）：" +
            "${tally.nameA} ${tally.winsA} : ${tally.winsB} ${tally.nameB}" +
            " · 已赛 ${tally.games} 局"
        if (tally.winsA >= config.seriesTarget ||
            tally.winsB >= config.seriesTarget
        ) {
            val champ = if (tally.winsA >= config.seriesTarget) {
                tally.nameA
            } else {
                tally.nameB
            }
            seriesNote = "$scoreLine\n系列赛结束，冠军：$champ"
        } else {
            seriesNote = "$scoreLine\n即将交换黑白，开始下一局…"
            delay(2600)
            val oldBlack = sideBlack
            setSideCfg(Stone.BLACK, sideWhite)
            setSideCfg(Stone.WHITE, oldBlack)
            onRestart()
        }
    }

    val finished = game.finished
    // If the engine failed to start, never soft-lock the game: the human
    // can keep playing both sides manually.
    val humanTurn = !aiControls(game.current) || failedFor(game.current)
    val anyHumanSide = !takeoverBlack || !takeoverWhite
    val analysisAllowed = !analyzing && !aiThinking && !bothAi || (bothAi && !eveRunning && !analyzing && !aiThinking)
    val judgmentActive = judgment != null && judgmentKey == game.moves.size
    val status = when {
        finished && game.timeoutBy != null ->
            "对局结束：${if (game.timeoutBy == Stone.BLACK) "黑" else "白"}方超时"
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
        // Undoing during an AI-vs-AI run pauses it, so the position
        // stays where the user put it instead of playing on at once.
        if (bothAi) eveRunning = false
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

    // Dead stones for the judgment/territory display, from the
    // same group-aware estimate the panels count with, so the
    // board marks and the numbers always agree.
    val judgedDeadSet: Set<Int> = when {
        focusMode || scoringMode -> emptySet()
        judgmentActive -> judgment?.let { j ->
            val st = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            TerritoryEstimate.deadStones(
                j.ownershipBlack, st, game.size.width, game.size.height,
                SettingsStore.deadGroupMean(settings.deadStrictness)
            )
        } ?: emptySet()
        showTerritory -> analysis?.let { a ->
            val st = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            TerritoryEstimate.deadStones(
                a.ownershipBlack, st, game.size.width, game.size.height,
                SettingsStore.deadGroupMean(settings.deadStrictness)
            )
        } ?: emptySet()
        else -> emptySet()
    }

    if (fullscreen) {
        // Fullscreen spectating / playing: board as large as possible,
        // no coordinates, only analysis + judgment + exit stay.
        Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = status, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    if (clockEnabled && !finished) {
                        TextButton(onClick = { userPaused = !userPaused }) {
                            Text(if (userPaused) "继续" else "暂停")
                        }
                    }
                    TextButton(onClick = {
                        PositionShare.share(
                            context, game,
                            "对局 · 第 ${game.moves.size} 手 · " +
                                if (game.current == Stone.BLACK) "黑方行棋" else "白方行棋"
                        )
                    }) { Text("分享") }
                    TextButton(onClick = { focusMode = !focusMode }) {
                    Text(if (focusMode) "退出专注" else "专注")
                }
                TextButton(onClick = { fullscreen = false }) { Text("退出全屏") }
                }
            }
            val fsA = analysis
            val fsSel = fsA?.candidates?.getOrNull(selectedCandidate)
            val fsOwnership = when {
                focusMode -> null
                scoringMode -> null
                judgmentActive -> judgment?.ownershipBlack
                showTerritory -> fsA?.ownershipBlack
                else -> null
            }
            BoardView(
                game = game,
                enabled = scoringMode || (!finished && !userPaused && humanTurn && !aiThinking),
                skin = BoardSkin.fromId(settings.boardSkin),
                showCoordinates = false,
                territoryThreshold = 0.5,
                candidates = if (!focusMode && !judgmentActive && !scoringMode) {
                    fsA?.candidates?.mapNotNull { cand ->
                        cand.move?.let { CandidateMark(it.x, it.y, fsA.candidates.indexOf(cand) + 1) }
                    } ?: emptyList()
                } else emptyList(),
                ownership = fsOwnership,
                pvGhost = if (!focusMode && !judgmentActive && !scoringMode) {
                    fsSel?.pvMoves?.take(7) ?: emptyList()
                } else emptyList(),
                preview = if (scoringMode) null else pendingPoint,
                markedDead = if (scoringMode) deadSet else emptySet(),
                judgedDead = judgedDeadSet,
                territorySpread = settings.territorySpread,
                territoryIntensity = settings.territoryIntensity,
                showMoveNumbers = settings.showMoveNumbers,
                oneColor = config.oneColor && !showTrueColors,
                flipped = boardFlipped
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
            Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                if (!focusMode && fsRes != null) {
                    val bw = fsRes.rootWinrateBlack.toFloat().coerceIn(0.02f, 0.98f)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Box(
                            modifier = Modifier.weight(bw).height(6.dp)
                                .background(Color(0xFF263238))
                        )
                        Box(
                            modifier = Modifier.weight(1f - bw).height(6.dp)
                                .background(Color(0xFFCFD8DC))
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                if (!focusMode && fsRes != null) {
                    Text(
                        text = "黑胜率 ${formatPercent(fsRes.rootWinrateBlack)} · " +
                            "${if (fsRes.rootScoreLeadBlack >= 0) "黑" else "白"}领先约 " +
                            "${formatNum(kotlin.math.abs(fsRes.rootScoreLeadBlack))} 目",
                        fontSize = 13.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                } else if (aiThinking) {
                    Box(modifier = Modifier.padding(top = 3.dp)) {
                        ThinkingIndicator()
                    }
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
                if (!focusMode) {
                    val analysisFreshFs = analysis != null && analysisKey == game.moves.size
                    when {
                        analyzing -> Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("分析中…")
                        }
                        analysisFreshFs -> Button(
                            onClick = {
                                analysis = null
                                analysisKey = -1
                                selectedCandidate = 0
                                analysisStatus = ""
                                continuousAnalysis = false
                                if (!judgmentActive) showTerritory = false
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("关闭分析") }
                        else -> Button(
                            onClick = { runAnalysis(false) },
                            enabled = analysisAllowed && !finished,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(
                                AppIconKind.CHART, Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("AI 分析")
                        }
                    }
                    val judgmentFreshFs = judgment != null && judgmentKey == game.moves.size
                    when {
                        judging -> Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("判断中…")
                        }
                        judgmentFreshFs -> Button(
                            onClick = { judgment = null; judgmentKey = -1 },
                            modifier = Modifier.weight(1f)
                        ) { Text("关闭形式判断") }
                        else -> Button(
                            onClick = { runJudgment() },
                            enabled = analysisAllowed && !finished,
                            modifier = Modifier.weight(1f)
                        ) {
                            AppIcon(
                                AppIconKind.JUDGE, Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("形式判断")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { fullscreen = false },
                    modifier = Modifier.weight(1f)
                ) { Text("退出全屏") }
            }
            if (!scoringMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    ) {
                        AppIcon(AppIconKind.PASS, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("停一手")
                    }
                    OutlinedButton(
                        onClick = { doUndo() },
                        enabled = game.moves.isNotEmpty() &&
                            (!bothAi || !eveRunning),
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(AppIconKind.UNDO, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("悔棋")
                    }
                    OutlinedButton(
                        onClick = { showResignConfirm = true },
                        enabled = !finished && anyHumanSide,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(AppIconKind.FLAG, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("认输")
                    }
                }
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
            .padding(horizontal = 2.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            TextButton(onClick = { requestExit() }) { Text("‹ 返回") }
            Text(
                text = if (userPaused) "已暂停" else status,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (clockEnabled && !finished) {
                TextButton(onClick = { userPaused = !userPaused }) {
                    Text(if (userPaused) "继续" else "暂停")
                }
            }
            TextButton(onClick = { focusMode = !focusMode }) {
                Text(if (focusMode) "退出专注" else "专注")
            }
            TextButton(onClick = {
                PositionShare.share(
                    context, game,
                    "对局 · 第 ${game.moves.size} 手 · " +
                        if (game.current == Stone.BLACK) "黑方行棋" else "白方行棋"
                )
            }) { Text("分享") }
            if (config.oneColor) {
                TextButton(onClick = { showTrueColors = !showTrueColors }) {
                    Text(if (showTrueColors) "一色" else "显色")
                }
            }
            if (finished && resultDismissed) {
                TextButton(onClick = { resultDismissed = false }) {
                    Text("结果")
                }
            }
            TextButton(onClick = { fullscreen = true }) { Text("全屏") }
        }
        // Player cards: who plays each side, captures and that
        // side's remaining clock. The side to move is tinted and
        // outlined so the turn is obvious at a glance.
        fun fmtSide(mainMs: Long, periods: Int, periodMs: Long): String =
            if (mainMs > 0) {
                val s = (mainMs + 999) / 1000
                String.format("%d:%02d", s / 60, s % 60)
            } else if (periods > 0) {
                "读秒 ${(periodMs + 999) / 1000} 秒 × $periods"
            } else {
                "时间到"
            }
        val blackModelName = remember(sideBlack.modelId) {
            ModelStore.list(context).firstOrNull { it.id == sideBlack.modelId }
                ?.displayName ?: "轻量模型"
        }
        val whiteModelName = remember(sideWhite.modelId) {
            ModelStore.list(context).firstOrNull { it.id == sideWhite.modelId }
                ?.displayName ?: "轻量模型"
        }
        fun sideTitle(stone: Stone): String = when {
            isEve -> if (stone == Stone.BLACK) blackModelName else whiteModelName
            isPve && stone != config.humanStone ->
                if (stone == Stone.BLACK) blackModelName else whiteModelName
            isPve -> "你"
            else -> if (stone == Stone.BLACK) "黑方" else "白方"
        }
        fun sideDetail(stone: Stone): String {
            val parts = mutableListOf<String>()
            parts += "提子 ${
                if (stone == Stone.BLACK) game.capturesByBlack else game.capturesByWhite
            }"
            if (clockEnabled) {
                parts += if (stone == Stone.BLACK) {
                    fmtSide(clockBlackMainMs, clockBlackPeriods, clockBlackPeriodMs)
                } else {
                    fmtSide(clockWhiteMainMs, clockWhitePeriods, clockWhitePeriodMs)
                }
            }
            if (aiThinking && !finished && game.current == stone) {
                parts += "思考中…"
            }
            return parts.joinToString(" · ")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerCard(
                modifier = Modifier.weight(1f),
                isBlack = true,
                title = sideTitle(Stone.BLACK),
                detail = sideDetail(Stone.BLACK),
                active = !finished && game.current == Stone.BLACK,
                alert = clockEnabled && game.current == Stone.BLACK &&
                    clockBlackMainMs <= 0L && clockBlackPeriods > 0
            )
            PlayerCard(
                modifier = Modifier.weight(1f),
                isBlack = false,
                title = sideTitle(Stone.WHITE),
                detail = sideDetail(Stone.WHITE),
                active = !finished && game.current == Stone.WHITE,
                alert = clockEnabled && game.current == Stone.WHITE &&
                    clockWhiteMainMs <= 0L && clockWhitePeriods > 0
            )
        }
        if (config.handicap > 0 || config.customStones.isNotEmpty()) {
            Text(
                text = listOfNotNull(
                    if (config.handicap > 0) "让 ${config.handicap} 子" else null,
                    if (config.customStones.isNotEmpty()) "自定义布局" else null
                ).joinToString(" · "),
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // Live score line: territory (incl. dead stones) + alive stones
        // + winrate, from the freshest analysis for this exact position
        // (manual analysis, judgment, or the automatic light analysis).
        val liveRes = when {
            analysis != null && analysisKey == game.moves.size -> analysis
            judgmentActive -> judgment
            autoKey == game.moves.size -> autoResult
            else -> null
        }
        // Mini win-rate bar: always occupies its strip so the board
        // does not shift when data arrives.
        Box(modifier = Modifier.fillMaxWidth().height(6.dp)) {
            if (!focusMode && liveRes != null) {
                val wr = liveRes.rootWinrateBlack.toFloat().coerceIn(0.02f, 0.98f)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(wr)
                            .height(6.dp)
                            .background(Color(0xFF263238))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f - wr)
                            .height(6.dp)
                            .background(Color(0xFFCFD8DC))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Fixed height whether or not an estimate is ready yet, so the
        // board never gets pushed down when the numbers arrive.
        Box(modifier = Modifier.fillMaxWidth().height(18.dp)) {
            if (!focusMode && liveRes != null) {
                val stonesArr = Array(game.size.area) { i ->
                    game.stoneAt(i % game.size.width, i / game.size.width)
                }
                val est = TerritoryEstimate.estimate(
                    liveRes.ownershipBlack, stonesArr, game.size.width, game.size.height, 0.5
                )
                val deadLive = TerritoryEstimate.deadStones(
                    liveRes.ownershipBlack, stonesArr, game.size.width, game.size.height,
                    SettingsStore.deadGroupMean(settings.deadStrictness)
                )
                var aliveB = 0
                var aliveW = 0
                for (i in stonesArr.indices) {
                    val s = stonesArr[i] ?: continue
                    if (i !in deadLive) {
                        if (s == Stone.BLACK) aliveB++ else aliveW++
                    }
                }
                val blackTotal = est.blackTerritory + aliveB
                val whiteTotal = est.whiteTerritory + aliveW + game.komi
                Text(
                    text = "形势：黑约 ${blackTotal} 目 · 白约 ${formatNum(whiteTotal)} 目 · 黑胜率 ${formatPercent(liveRes.rootWinrateBlack)}（点开详情）",
                    fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.clickable { showScoreDetail = true }
                )
            } else if (!focusMode) {
                Text(
                    text = "形势：等待分析…",
                    fontSize = 12.sp, color = Color.LightGray
                )
            }
        }
        if (showScoreDetail && liveRes != null) {
            val stonesArr2 = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            val est2 = TerritoryEstimate.estimate(
                liveRes.ownershipBlack, stonesArr2, game.size.width, game.size.height, 0.5
            )
            val deadLive2 = TerritoryEstimate.deadStones(
                liveRes.ownershipBlack, stonesArr2, game.size.width, game.size.height,
                SettingsStore.deadGroupMean(settings.deadStrictness)
            )
            var aliveB2 = 0
            var aliveW2 = 0
            var deadB2 = 0
            var deadW2 = 0
            for (i in stonesArr2.indices) {
                val s = stonesArr2[i] ?: continue
                if (i in deadLive2) {
                    if (s == Stone.BLACK) deadB2++ else deadW2++
                } else if (s == Stone.BLACK) {
                    aliveB2++
                } else {
                    aliveW2++
                }
            }
            val lead = liveRes.rootScoreLeadBlack
            AlertDialog(
                onDismissRequest = { showScoreDetail = false },
                title = { Text("形势详情（AI 估算）") },
                text = {
                    Text(
                        "黑胜率 ${formatPercent(liveRes.rootWinrateBlack)} · " +
                            "目差 ${if (lead >= 0) "黑多" else "白多"} ${formatNum(kotlin.math.abs(lead))} 目\n" +
                            "黑：活子 $aliveB2 + 空 ${est2.blackTerritory} = ${est2.blackTerritory + aliveB2} 目\n" +
                            "白：活子 $aliveW2 + 空 ${est2.whiteTerritory} + 贴目 ${formatNum(game.komi)} = " +
                            "${formatNum(est2.whiteTerritory + aliveW2 + game.komi)} 目\n" +
                            "估算死子：黑 $deadB2 子 · 白 $deadW2 子\n" +
                            "（对局进行中的估算，终局数目以实际点目为准）",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showScoreDetail = false }) { Text("关闭") }
                }
            )
        }
        // Fixed-height status strip: the AI-thinking text and the
        // tap-to-confirm hint live here so the board never jumps up or
        // down when they appear and disappear.
        Box(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            if (aiThinking) {
                ThinkingIndicator()
            }
            val hint = when {
                pendingPoint != null -> "已选中落子点，再点一次确认落子"
                else -> engineStatus
            }
            if (!aiThinking && hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 13.sp,
                    color = if (pendingPoint != null) Rose else Color(0xFF2F7CF6),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (finished) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onReview(buildLibraryGame()) },
                    modifier = Modifier.weight(1f)
                ) { Text("查看复盘") }
                OutlinedButton(
                    onClick = { showSaveToLibrary = true },
                    modifier = Modifier.weight(1f),
                    enabled = !savedToLibrary
                ) { Text(if (savedToLibrary) "已收藏" else "收藏棋谱") }
            }
            Spacer(modifier = Modifier.height(6.dp))
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
            enabled = scoringMode || (!finished && !userPaused && humanTurn && !aiThinking),
            skin = BoardSkin.fromId(settings.boardSkin),
            showCoordinates = settings.showCoordinates,
            territoryThreshold = 0.5,
            candidates = if (!focusMode && !judgmentActive && !scoringMode) {
                currentAnalysis?.candidates?.mapNotNull { cand ->
                    cand.move?.let { CandidateMark(it.x, it.y, currentAnalysis.candidates.indexOf(cand) + 1) }
                } ?: emptyList()
            } else emptyList(),
            ownership = if (focusMode) null else displayOwnership,
            pvGhost = if (!focusMode && !judgmentActive && !scoringMode) selected?.pvMoves?.take(7) ?: emptyList() else emptyList(),
            humanPoint = if (!focusMode && !judgmentActive && !scoringMode) {
                currentAnalysis?.humanTopMove?.let { it.x to it.y }
            } else {
                null
            },
            preview = if (scoringMode) null else pendingPoint,
            markedDead = if (scoringMode) deadSet else emptySet(),
            judgedDead = judgedDeadSet,
            territorySpread = settings.territorySpread,
            territoryIntensity = settings.territoryIntensity,
            showMoveNumbers = settings.showMoveNumbers,
            oneColor = config.oneColor && !showTrueColors,
            flipped = boardFlipped
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
        if (judgmentActive && !focusMode) {
            val j = judgment!!
            val stones = Array(game.size.area) { i ->
                game.stoneAt(i % game.size.width, i / game.size.width)
            }
            val terr = TerritoryEstimate.estimate(
                j.ownershipBlack, stones, game.size.width, game.size.height, 0.5
            )
            val deadJ = TerritoryEstimate.deadStones(
                j.ownershipBlack, stones, game.size.width, game.size.height,
                SettingsStore.deadGroupMean(settings.deadStrictness)
            )
            var blackDead = 0
            var whiteDead = 0
            for (i in deadJ) {
                if (stones[i] == Stone.WHITE) whiteDead++
                if (stones[i] == Stone.BLACK) blackDead++
            }
            // Counted score, Chinese style: live stones + territory
            // points (dead stones already count for the opponent in
            // the territory numbers). The breakdown must add up to
            // the verdict, like the score panels of the big Go apps.
            var aliveBlack = 0
            var aliveWhite = 0
            for (i in stones.indices) {
                if (i in deadJ) continue
                if (stones[i] == Stone.BLACK) aliveBlack++
                if (stones[i] == Stone.WHITE) aliveWhite++
            }
            val blackScore = aliveBlack + terr.blackTerritory
            val whiteScore = aliveWhite + terr.whiteTerritory + game.komi
            val diff = blackScore - whiteScore
            androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(text = "形势判断", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Rose)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when {
                        diff > 0.25 -> "黑胜约 ${formatNum(diff)} 目"
                        diff < -0.25 -> "白胜约 ${formatNum(-diff)} 目"
                        else -> "局势接近"
                    },
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Rose
                )
            }
            // Score share bar: black share vs white share.
            if (settings.judgeShowBar) {
            Spacer(modifier = Modifier.height(8.dp))
            val shareTotal = (blackScore + whiteScore).coerceAtLeast(1.0)
            Row(modifier = Modifier.fillMaxWidth().height(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight((blackScore / shareTotal).coerceIn(0.02, 0.98).toFloat())
                        .height(12.dp)
                        .background(Color(0xFF1A1A1A), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .weight((whiteScore / shareTotal).coerceIn(0.02, 0.98).toFloat())
                        .height(12.dp)
                        .background(Color(0xFFE8E8E8), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                )
            }
            }
            if (settings.judgeShowDetail) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "黑：活子 $aliveBlack + 空 ${terr.blackTerritory} = ${formatNum(blackScore.toDouble())}",
                fontSize = 13.sp, color = Color.Gray
            )
            Text(
                text = "白：活子 $aliveWhite + 空 ${terr.whiteTerritory} + 贴目 ${formatNum(game.komi)} = ${formatNum(whiteScore)}",
                fontSize = 13.sp, color = Color.Gray
            )
            if (blackDead + whiteDead > 0) {
                Text(
                    text = "判死子：黑 $blackDead 子 · 白 $whiteDead 子（已算进对方的地里）",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
            if (terr.neutral > 0) {
                Text(
                    text = "未定区域约 ${terr.neutral} 点，下完官子后判断会更准",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
            }
            // Confidence from how decisive the ownership looks on the
            // empty points: lots of pale (undecided) points means the
            // verdict is still soft.
            var emptyPoints = 0
            var unclearPoints = 0
            for (i in stones.indices) {
                if (stones[i] != null) continue
                emptyPoints++
                val mag = kotlin.math.abs(j.ownershipBlack.getOrElse(i) { 0.0 })
                if (mag < 0.5) unclearPoints++
            }
            val unclearShare = if (emptyPoints > 0) {
                unclearPoints.toDouble() / emptyPoints
            } else {
                0.0
            }
            if (settings.judgeShowConfidence) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    unclearShare > 0.35 -> "可信度：一般 — 不少区域还在争夺中（棋盘上颜色越深归属越确定）"
                    unclearShare > 0.15 -> "可信度：较高 — 大部分区域已经定型"
                    else -> "可信度：高 — 局面基本定型"
                } + " · 引擎估算，仅供参考",
                fontSize = 12.sp, color = Color.Gray
            )
            }
            }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---- Analysis panel ----
        if (!focusMode && !judgmentActive && (analyzing || analysisStatus.isNotEmpty() || currentAnalysis != null)) {
            if (analyzing || (currentAnalysis == null && analysisStatus.isNotEmpty())) {
                Text(text = analysisStatus, fontSize = 13.sp, color = Color(0xFF2F7CF6))
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (currentAnalysis != null) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI 分析", fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = Rose
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "连续分析", fontSize = 12.sp, color = Color.Gray)
                    Switch(
                        checked = continuousAnalysis,
                        onCheckedChange = { continuousAnalysis = it }
                    )
                    TextButton(onClick = { analysisExpanded = !analysisExpanded }) {
                        Text(if (analysisExpanded) "收起" else "展开")
                    }
                }
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
                Text(
                    text = commentaryLine(
                        wr, lead,
                        kotlin.math.abs(
                            (currentAnalysis.candidates.getOrNull(0)?.winrateBlack ?: 0.0) -
                                (currentAnalysis.candidates.getOrNull(1)?.winrateBlack ?: 0.0)
                        )
                    ),
                    fontSize = 13.sp, color = Color(0xFF2F7CF6)
                )
                val topCand = currentAnalysis.candidates.firstOrNull()
                if (topCand != null) {
                    Text(
                        text = "一选：${topCand.vertex} · 胜率 ${formatPercent(topCand.winrateBlack)}",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    val tactical = remember(currentAnalysis, game.moves.size) {
                        tacticalLine(game, topCand)
                    }
                    if (tactical != null) {
                        Text(
                            text = tactical,
                            fontSize = 13.sp, color = Color(0xFF2F7CF6)
                        )
                    }
                }
                if (currentAnalysis.humanTopVertex.isNotEmpty()) {
                    val hv = currentAnalysis.humanTopVertex
                    Text(
                        text = when {
                            hv == "pass" -> "人类一选：停一手（真人高手多半会停）"
                            hv == topCand?.vertex ->
                                "人类一选：$hv · 真人概率约 ${formatPercent(currentAnalysis.humanTopProb)}（和 AI 一选相同）"
                            else ->
                                "人类一选：$hv · 真人概率约 ${formatPercent(currentAnalysis.humanTopProb)}（棋盘上的蓝圈）"
                        },
                        fontSize = 13.sp, color = Color(0xFF2F7CF6)
                    )
                }
                if (analysisExpanded) {
                val stones = Array(game.size.area) { i ->
                    game.stoneAt(i % game.size.width, i / game.size.width)
                }
                val terr = TerritoryEstimate.estimate(
                    currentAnalysis.ownershipBlack, stones,
                    game.size.width, game.size.height, 0.5
                )
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
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "换个深度重算", fontSize = 12.sp, color = Color.Gray)
                    FilterChip(
                        selected = false,
                        onClick = { runAnalysis(deep = false, visitsOverride = 150) },
                        enabled = !analyzing,
                        label = { Text("快速") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { runAnalysis(deep = false, visitsOverride = 400) },
                        enabled = !analyzing,
                        label = { Text("标准") }
                    )
                }
                if (analysisStatus.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = analysisStatus, fontSize = 12.sp, color = Color.Gray)
                }
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
            ) {
                AppIcon(AppIconKind.PASS, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("停一手")
            }
            OutlinedButton(
                onClick = { doUndo() },
                enabled = game.moves.isNotEmpty() && (!bothAi || !eveRunning),
                modifier = Modifier.weight(1f)
            ) {
                AppIcon(AppIconKind.UNDO, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("悔棋")
            }
            OutlinedButton(
                onClick = { showResignConfirm = true },
                enabled = !finished && anyHumanSide,
                modifier = Modifier.weight(1f)
            ) {
                AppIcon(AppIconKind.FLAG, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("认输")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!focusMode) {
                val analysisFresh = analysis != null && analysisKey == game.moves.size
                when {
                    analyzing -> OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("分析中…")
                    }
                    analysisFresh -> Button(
                        onClick = {
                            analysis = null
                            analysisKey = -1
                            selectedCandidate = 0
                            analysisStatus = ""
                            continuousAnalysis = false
                            if (!judgmentActive) showTerritory = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("关闭分析") }
                    else -> OutlinedButton(
                        onClick = { runAnalysis(deep = false) },
                        enabled = analysisAllowed && !finished,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(AppIconKind.CHART, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("AI 分析")
                    }
                }
                val judgmentFresh = judgment != null && judgmentKey == game.moves.size
                when {
                    judging -> OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("判断中…")
                    }
                    judgmentFresh -> Button(
                        onClick = { judgment = null; judgmentKey = -1 },
                        modifier = Modifier.weight(1f)
                    ) { Text("关闭形式判断") }
                    else -> OutlinedButton(
                        onClick = { runJudgment() },
                        enabled = !analyzing && !aiThinking && !finished,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIcon(AppIconKind.JUDGE, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("形式判断")
                    }
                }
            }
            OutlinedButton(
                onClick = { enterScoring() },
                enabled = !finished && !scoringBusy && !aiThinking && (!bothAi || !eveRunning),
                modifier = Modifier.weight(1f)
            ) {
                AppIcon(AppIconKind.STATS, Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(if (scoringBusy) "判定中…" else "终局数目")
            }
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
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (boardFlipped) "恢复棋盘方向" else "翻转棋盘（白方视角）") },
                        onClick = {
                            showMoreMenu = false
                            boardFlipped = !boardFlipped
                            pendingPoint = null
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                if (refereeEnabled) {
                                    "智能裁判：已开（点关）"
                                } else {
                                    "智能裁判：已关（点开）"
                                }
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            refereeEnabled = !refereeEnabled
                            SettingsStore.save(
                                context,
                                settings.copy(refereeEnabled = refereeEnabled)
                            )
                        }
                    )
                }
            }
        }
        }
    }
    }

    if (showAiPanel) {
        val allModels = remember { ModelStore.list(context) }
        val models = allModels.filter { !it.isHumanOnly }
        val humanInstalled =
            allModels.firstOrNull { it.id == ModelStore.HUMAN_ID }?.installed == true
        AlertDialog(
            onDismissRequest = { showAiPanel = false },
            title = { Text("AI 托管设置") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AiSideSection(
                        stone = Stone.BLACK, cfg = sideBlack, takenOver = takeoverBlack,
                        models = models,
                        humanInstalled = humanInstalled,
                        onDownloadStrong = {
                            showAiPanel = false
                            showStrongDownload = true
                        },
                        onToggle = { setTakeover(Stone.BLACK, it) },
                        onCfgChange = { setSideCfg(Stone.BLACK, it) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AiSideSection(
                        stone = Stone.WHITE, cfg = sideWhite, takenOver = takeoverWhite,
                        models = models,
                        humanInstalled = humanInstalled,
                        onDownloadStrong = {
                            showAiPanel = false
                            showStrongDownload = true
                        },
                        onToggle = { setTakeover(Stone.WHITE, it) },
                        onCfgChange = { setSideCfg(Stone.WHITE, it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "黑白双方可以用不同模型对打。换模型会重启该方引擎（几秒），改强度立即生效。设置会自动保存。",
                        fontSize = 12.sp, color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "智能裁判", fontSize = 14.sp)
                            Text(
                                text = "局面完全收官时，裁判自动判定胜负：先展示结果，3 秒后判终",
                                fontSize = 12.sp, color = Color.Gray
                            )
                        }
                        Switch(
                            checked = refereeEnabled,
                            onCheckedChange = {
                                refereeEnabled = it
                                SettingsStore.save(
                                    context,
                                    settings.copy(refereeEnabled = it)
                                )
                            }
                        )
                    }
                    Text(
                        text = "AI 在绝境时会提出认输，需要你同意；拒绝后至少 15 手它才能再提。",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAiPanel = false }) { Text("关闭") }
            }
        )
    }

    if (resignProposal != null) {
        val stone = resignProposal!!
        val refuseResign = {
            if (stone == Stone.BLACK) {
                resignRefusedAtBlack = game.moves.size
            } else {
                resignRefusedAtWhite = game.moves.size
            }
            resignProposal = null
        }
        AlertDialog(
            onDismissRequest = refuseResign,
            title = { Text("AI 想认输") },
            text = {
                Text(
                    "${if (stone == Stone.BLACK) "黑方" else "白方"} AI 判断自己已经没有胜算，想认输。" +
                        "同意后对局立即结束、判你胜；拒绝后它会继续下，至少 15 手后才能再提认输。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resignProposal = null
                        game.resign(stone)
                        version++
                        resultDismissed = false
                    }
                ) { Text("同意认输") }
            },
            dismissButton = {
                TextButton(onClick = refuseResign) { Text("继续对局") }
            }
        )
    }

    if (refereeNotice != null) {
        var countdown by remember(refereeNotice) { mutableIntStateOf(3) }
        LaunchedEffect(refereeNotice) {
            countdown = 3
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            finishReferee()
        }
        AlertDialog(
            onDismissRequest = {
                refereeNotice = null
                refereeSnoozeUntil = game.moves.size + 6
                settledStreak = 0
            },
            title = { Text("智能裁判") },
            text = {
                Text(
                    "局面已经完全收官，裁判判定：\n${refereeNotice!!.first}\n\n" +
                        "$countdown 秒后自动判终。想继续下可以关掉这次判定，裁判过一阵才会再提醒。"
                )
            },
            confirmButton = {
                TextButton(onClick = { finishReferee() }) { Text("立即判定") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        refereeNotice = null
                        refereeSnoozeUntil = game.moves.size + 6
                        settledStreak = 0
                    }
                ) { Text("继续下") }
            }
        )
    }

    if (showStrongDownload) {
        StrongModelDialog(
            onDismiss = { showStrongDownload = false },
            onChanged = { }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出对局？") },
            text = {
                Text("这局还没下完，退出后不会保留进度（已结束的对局会自动存入棋谱库）。确定退出吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    exitGame()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("继续下棋")
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

    if (showSaveToLibrary) {
        SaveGameDialog(
            game = buildLibraryGame(),
            onDismiss = { showSaveToLibrary = false },
            onSaved = {
                savedToLibrary = true
                showSaveToLibrary = false
            }
        )
    }

    if (finished && !resultDismissed) {
        val resultText = if (game.timeoutBy != null) {
            "${if (game.timeoutBy == Stone.BLACK) "白" else "黑"}方胜（对方超时）"
        } else if (game.resignedBy != null) {
            "${if (game.resignedBy == Stone.BLACK) "白" else "黑"}方胜（对方认输）"
        } else {
            val s = game.scoringDead?.let { game.scoreWithDead(it) } ?: game.score()
            val winner = if (s.lead > 0) "黑" else "白"
            val diff = kotlin.math.abs(s.lead)
            "${winner}方胜 ${formatNum(diff)} 目\n" +
                "黑：棋子 ${s.blackStones} + 空 ${s.blackTerritory} = ${s.blackTotal.toInt()}\n" +
                "白：棋子 ${s.whiteStones} + 空 ${s.whiteTerritory} + 贴目 ${s.komi} = ${s.whiteTotal}"
        }
        // The single biggest win-rate swing of the game, from the
        // live-recorded per-turn stats.
        val swing: Pair<Int, Double>? = run {
            val keys = liveStats.keys.sorted()
            var best: Pair<Int, Double>? = null
            for (i in 1 until keys.size) {
                if (keys[i] != keys[i - 1] + 1) continue
                val d = kotlin.math.abs(
                    liveStats[keys[i]]!!.winrateBlack - liveStats[keys[i - 1]]!!.winrateBlack
                )
                if (best == null || d > best!!.second) best = keys[i] to d
            }
            best
        }
        AlertDialog(
            onDismissRequest = { resultDismissed = true },
            title = { Text("对局结果") },
            text = {
                Column {
                    Text(resultText)
                    if (swing != null && swing.second >= 0.05) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "全场转折：第 ${swing.first} 手（胜率波动 " +
                                String.format("%.1f%%", swing.second * 100) + "）",
                            fontSize = 13.sp, color = Color.Gray
                        )
                        TextButton(
                            onClick = {
                                resultDismissed = true
                                onReviewAtPly(buildLibraryGame(), swing.first)
                            }
                        ) { Text("跳到转折手复盘") }
                    }
                    if (seriesNote.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            seriesNote,
                            fontSize = 13.sp, color = Color(0xFF2F7CF6)
                        )
                    }
                    TextButton(onClick = { onRestart() }) { Text("再来一局（相同设置）") }
                    TextButton(
                        onClick = {
                            PositionShare.share(context, game, "对局结束 · $resultText")
                        }
                    ) { Text("分享结果图") }
                    TextButton(
                        onClick = {
                            Thread {
                                val ok = PositionShare.saveToGallery(
                                    context, game, "对局结束 · $resultText"
                                )
                                android.os.Handler(
                                    android.os.Looper.getMainLooper()
                                ).post {
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) "已保存到相册" else "保存失败",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }.start()
                        }
                    ) { Text("保存到相册") }
                }
            },
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

/** One plain-language sentence about the position: who is ahead,
 * by how much, and how much the top move stands out. */
/** Liberties of the group containing the given board indices. */
private fun groupLiberties(g: GoGame, group: Set<Int>): Int {
    val libs = HashSet<Int>()
    val w = g.size.width
    val h = g.size.height
    for (i in group) {
        val x = i % w
        val y = i / w
        if (x > 0 && g.stoneAt(x - 1, y) == null) libs.add(i - 1)
        if (x < w - 1 && g.stoneAt(x + 1, y) == null) libs.add(i + 1)
        if (y > 0 && g.stoneAt(x, y - 1) == null) libs.add(i - w)
        if (y < h - 1 && g.stoneAt(x, y + 1) == null) libs.add(i + w)
    }
    return libs.size
}

/** Board-region name ("左上", "右下", "中央"...) for a point set. */
private fun regionName(points: Set<Int>, w: Int, h: Int): String {
    if (points.isEmpty()) return ""
    val ax = points.map { it % w }.average()
    val ay = points.map { it / w }.average()
    val col = when {
        ax < w / 3.0 -> "左"
        ax > w * 2.0 / 3.0 -> "右"
        else -> ""
    }
    val row = when {
        ay < h / 3.0 -> "上"
        ay > h * 2.0 / 3.0 -> "下"
        else -> ""
    }
    val name = col + row
    return if (name.isEmpty()) "中央" else name
}

/**
 * A concrete tactical reading of the AI's top choice, in the
 * style of a human commentator: "一选逼住右上的黑棋，它只剩
 * 2 气了". Every claim is verified by actually playing the
 * candidate's principal variation on a scratch board and
 * comparing group liberties before/after — nothing is claimed
 * that the simulation does not show. Returns null when the PV
 * does nothing concrete enough to narrate.
 */
private fun tacticalLine(
    game: GoGame,
    top: com.tuxungo.android.engine.Candidate
): String? {
    if (top.pvMoves.isEmpty()) return null
    val mover = game.current
    val foe = mover.other()
    val w = game.size.width
    val h = game.size.height
    val scratch = GoGame(game.size, game.komi)
    val stones = ArrayList<Move>()
    for (y in 0 until h) {
        for (x in 0 until w) {
            val s = game.stoneAt(x, y)
            if (s != null) stones.add(Move(s, x, y))
        }
    }
    scratch.loadPosition(stones, mover)
    data class GroupInfo(val stones: Set<Int>, val libs: Int)
    fun collect(color: Stone): List<GroupInfo> {
        val seen = HashSet<Int>()
        val out = ArrayList<GroupInfo>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (scratch.stoneAt(x, y) == color && idx !in seen) {
                    val grp = scratch.groupAt(x, y)
                    seen.addAll(grp)
                    out.add(GroupInfo(grp, groupLiberties(scratch, grp)))
                }
            }
        }
        return out
    }
    val foeBefore = collect(foe)
    val ownBefore = collect(mover)
    var played = 0
    for (mv in top.pvMoves.take(7)) {
        if (mv.isPass) {
            scratch.pass()
            played++
            continue
        }
        if (!scratch.play(mv.x, mv.y)) break
        played++
    }
    if (played == 0) return null
    val foeName = if (foe == Stone.BLACK) "黑棋" else "白棋"
    val ownName = if (mover == Stone.BLACK) "黑棋" else "白棋"
    // Capture: an enemy group that existed before is gone now.
    for (g in foeBefore) {
        val alive = g.stones.count {
            scratch.stoneAt(it % w, it / w) == foe
        }
        if (alive == 0 && g.stones.size >= 2) {
            return "照一选下下去，${regionName(g.stones, w, h)}这块$foeName" +
                "（${g.stones.size} 子）会被提掉，接不归了。"
        }
    }
    // Squeeze: an enemy group lost most of its air.
    for (g in foeBefore) {
        val rep = g.stones.firstOrNull {
            scratch.stoneAt(it % w, it / w) == foe
        } ?: continue
        val now = scratch.groupAt(rep % w, rep / w)
        val libs = groupLiberties(scratch, now)
        if (libs in 1..2 && libs < g.libs && g.stones.size >= 3) {
            return "一选逼住${regionName(g.stones, w, h)}的$foeName，" +
                "它只剩 $libs 气了，眼位也不足，很危险。"
        }
    }
    // Rescue: one of the mover's tight groups breathes again.
    for (g in ownBefore) {
        if (g.libs > 2) continue
        val rep = g.stones.firstOrNull {
            scratch.stoneAt(it % w, it / w) == mover
        } ?: continue
        val now = scratch.groupAt(rep % w, rep / w)
        val libs = groupLiberties(scratch, now)
        if (libs >= g.libs + 3 && g.stones.size >= 3) {
            return "一选先救${regionName(g.stones, w, h)}自己的$ownName，" +
                "它本来只剩 ${g.libs} 气，这下缓过来了。"
        }
    }
    return null
}

private fun commentaryLine(wrBlack: Double, leadBlack: Double, gap: Double): String {
    val pct = (wrBlack * 100).toInt()
    val side = if (leadBlack >= 0) "黑方" else "白方"
    val leadAbs = kotlin.math.abs(leadBlack)
    val base = when {
        leadAbs >= 30.0 ->
            "$side 优势很大，胜率约 $pct%，领先约 ${formatNum(leadAbs)} 目，这局基本拿下了"
        leadAbs >= 12.0 ->
            "$side 占优，胜率约 $pct%，领先约 ${formatNum(leadAbs)} 目"
        leadAbs >= 4.0 ->
            "$side 稍占上风，胜率约 $pct%，领先约 ${formatNum(leadAbs)} 目"
        else ->
            "局面很接近，胜率约 $pct%，谁先下出关键一手谁占优"
    }
    val tail = when {
        gap >= 0.05 -> "；一选明显强于其他着法，这手很关键"
        gap >= 0.02 -> "；一选比较突出，值得优先考虑"
        else -> "；几个候选着法差距不大"
    }
    return base + tail
}

/** Animated "AI is thinking" indicator: a small spinner plus
 * three dots that light up in turn — less monotonous than a
 * static label with an ellipsis. */
@Composable
fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF2F7CF6)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "AI 思考中", fontSize = 13.sp, color = Color(0xFF2F7CF6))
        Spacer(modifier = Modifier.width(4.dp))
        for (i in 0..2) {
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 450, delayMillis = i * 150
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "thinkingDot$i"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        Color(0xFF2F7CF6).copy(alpha = alpha),
                        androidx.compose.foundation.shape.CircleShape
                    )
            )
            if (i < 2) Spacer(modifier = Modifier.width(3.dp))
        }
    }
}

/** One side's controls in the AI panel: takeover switch, model picker, strength chips. */
@Composable
private fun AiSideSection(
    stone: Stone,
    cfg: SideAiConfig,
    takenOver: Boolean,
    models: List<ModelInfo>,
    humanInstalled: Boolean = true,
    onDownloadStrong: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onCfgChange: (SideAiConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var rankExpanded by remember { mutableStateOf(false) }
    val currentName = models.firstOrNull { it.id == cfg.modelId }?.displayName ?: "轻量模型"
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
                    ) {
                        Text(
                            "模型：$currentName",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        for (m in models) {
                            val needsDownload =
                                m.id == ModelStore.STRONG_ID && !m.installed
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        if (needsDownload) {
                                            "${m.displayName}（未下载 · 点此下载）"
                                        } else {
                                            m.displayName
                                        }
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    if (needsDownload) {
                                        onDownloadStrong()
                                    } else {
                                        onCfgChange(cfg.copy(modelId = m.id))
                                    }
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
                            label = { Text(label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = "思考量：${cfg.visits}（越大越强、越慢）",
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { rankExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = humanInstalled
                    ) {
                        Text("棋力定级：${SideAiStore.rankLabel(cfg.rank)}")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = rankExpanded,
                        onDismissRequest = { rankExpanded = false }
                    ) {
                        for (r in SideAiStore.RANKS) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(SideAiStore.rankLabel(r)) },
                                onClick = {
                                    rankExpanded = false
                                    onCfgChange(cfg.copy(rank = r))
                                }
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        !humanInstalled ->
                            "定级需要人类风格模型，它已删除，在模型库里可以免费恢复"
                        cfg.rank != "max" ->
                            "按${SideAiStore.rankLabel(cfg.rank)}的真实水平下棋：落子来自按这个级别真人棋谱训练的模型，会犯这个级别常犯的错，输赢都真实"
                        else ->
                            "Max 为 AI 全部实力；往下可定级到 18 级，陪练更对等"
                    },
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "模拟人类落子", fontSize = 14.sp)
                        Text(
                            text = if (cfg.rank != "max") {
                                "已定级时以定级为准，此开关不生效"
                            } else if (humanInstalled) {
                                "选点风格像真人棋手，强搜索兜底，不故意下错招"
                            } else {
                                "人类风格模型已删除，在模型库里可以免费恢复"
                            },
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                    Switch(
                        checked = cfg.humanLike && humanInstalled && cfg.rank == "max",
                        enabled = humanInstalled && cfg.rank == "max",
                        onCheckedChange = { onCfgChange(cfg.copy(humanLike = it)) }
                    )
                }
            }
        }
    }
}

/** One side's info card above the board in a game. */
@Composable
private fun PlayerCard(
    modifier: Modifier,
    isBlack: Boolean,
    title: String,
    detail: String,
    active: Boolean,
    alert: Boolean
) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (active) {
                colors.primaryContainer
            } else {
                colors.surfaceVariant
            }
        ),
        border = if (active) BorderStroke(1.5.dp, colors.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (isBlack) {
                            Modifier
                        } else {
                            Modifier.border(
                                BorderStroke(1.dp, Color(0xFFB0A58F)),
                                CircleShape
                            )
                        }
                    )
                    .background(
                        if (isBlack) Color(0xFF1A1A1A) else Color.White,
                        CircleShape
                    )
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    fontSize = 11.sp,
                    color = if (alert) Color(0xFFB3261E) else Color.Gray,
                    maxLines = 1
                )
            }
        }
    }
}
