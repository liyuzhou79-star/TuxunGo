package com.tuxungo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.delay
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
import com.tuxungo.android.PositionShare
import com.tuxungo.android.SettingsStore
import com.tuxungo.android.engine.AnalysisEngine
import com.tuxungo.android.engine.AnalysisMath
import com.tuxungo.android.engine.EngineAssets
import com.tuxungo.android.engine.KataEngine
import com.tuxungo.android.engine.MarkKind
import com.tuxungo.android.engine.ModelStore
import com.tuxungo.android.engine.MoveMark
import com.tuxungo.android.engine.TerritoryEstimate
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
    onContinue: (GameConfig) -> Unit,
    initialPly: Int = -1
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }
    var current by remember { mutableStateOf(game) }
    val totalMoves = current.moves.size

    // Stats arrive sparse: turns recorded live during the game are
    // already here, and only the missing ones get analyzed on open.
    var statsMap by remember {
        mutableStateOf(ReviewMath.decodeSparse(current.statsCache))
    }
    val turns: List<TurnStat>? =
        if (totalMoves > 0 && (0..totalMoves).all { it in statsMap }) {
            (0..totalMoves).map { statsMap[it]!! }
        } else {
            null
        }
    val missingTurns: List<Int> = (0..totalMoves).filter { it !in statsMap }
    var analyzing by remember { mutableStateOf(false) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(1) }
    var errorText by remember { mutableStateOf("") }
    val reviewPrefs = remember {
        context.getSharedPreferences("review_prefs", android.content.Context.MODE_PRIVATE)
    }
    // Review always opens the way the game was played (Black at
    // the bottom), matching the live game board. A persisted
    // white-perspective choice used to flip every later review
    // upside down; the perspective chips below still switch for
    // the current session only.
    var perspective by remember { mutableStateOf(Stone.BLACK) }
    var ply by remember {
        mutableIntStateOf(
            if (initialPly in 0..totalMoves) initialPly else totalMoves
        )
    }
    // Scrub feedback: dragging across the win-rate chart moves the
    // board move by move, and every move it passes gets the place
    // sound (softly) and a tiny vibration tick, like riffling
    // through the game on a real board.
    val scrubPool = remember {
        android.media.SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_GAME)
                    .setContentType(
                        android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()
            )
            .build()
    }
    val scrubSoundIds = remember {
        SettingsStore.placeSoundResList(settings.placeSound)
            .map { scrubPool.load(context, it, 1) }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { scrubPool.release() }
    }
    val scrubVibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(
                android.os.VibratorManager::class.java
            )
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                as? android.os.Vibrator
        }
    }

    fun scrubTo(newPly: Int) {
        if (newPly == ply) return
        ply = newPly
        if (settings.soundEnabled && scrubSoundIds.isNotEmpty()) {
            scrubPool.play(
                scrubSoundIds.random(), 0.35f, 0.35f, 1, 0,
                1.15f + kotlin.random.Random.nextFloat() * 0.1f
            )
        }
        if (settings.vibrationEnabled) {
            scrubVibrator?.vibrate(
                android.os.VibrationEffect.createOneShot(
                    12,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        }
    }
    // Branch ("what-if") exploration: a scratch game forked at the
    // current ply. The main line is never touched.
    var branchGame by remember { mutableStateOf<GoGame?>(null) }
    var showBranchExitConfirm by remember { mutableStateOf(false) }
    // Autoplay: step through the game hands-free at a chosen pace
    // (default 1.2 s per move). Any manual navigation stops it.
    var autoPlaying by remember { mutableStateOf(false) }
    var autoSpeedMs by remember {
        mutableIntStateOf(reviewPrefs.getInt("autoMs", 1200))
    }
    LaunchedEffect(autoPlaying, ply, autoSpeedMs) {
        if (!autoPlaying) return@LaunchedEffect
        if (branchGame != null || ply >= totalMoves) {
            autoPlaying = false
            return@LaunchedEffect
        }
        delay(autoSpeedMs.toLong())
        if (autoPlaying) scrubTo(ply + 1)
    }

    /** Leaving with an unsaved trial branch open asks first. */
    fun requestReviewExit() {
        if (branchGame != null) {
            showBranchExitConfirm = true
        } else {
            onBack()
        }
    }

    androidx.activity.compose.BackHandler { requestReviewExit() }
    var branchBasePly by remember { mutableIntStateOf(0) }
    var branchPending by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var branchInfo by remember { mutableStateOf("") }
    var branchBusy by remember { mutableStateOf(false) }
    var branchResult by remember {
        mutableStateOf<com.tuxungo.android.engine.AnalysisResult?>(null)
    }
    // GoGame is not observable; bump this after every branch mutation
    // so the board redraws.
    var branchVersion by remember { mutableIntStateOf(0) }
    var showBranchSave by remember { mutableStateOf(false) }
    var showBest by remember { mutableStateOf(true) }
    var showLead by remember { mutableStateOf(true) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var savedNote by remember { mutableStateOf(false) }
    var sgfCopied by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

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

    fun startAnalysis(visits: Int) {
        if (analyzing || totalMoves == 0) return
        val missing = (0..totalMoves).filter { it !in statsMap }
        if (missing.isEmpty()) return
        analyzing = true
        errorText = ""
        progressDone = 0
        progressTotal = missing.size
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
                    maxVisits = visits,
                    onlyTurns = missing.toSet(),
                    onProgress = { d, t ->
                        progressDone = d
                        progressTotal = t
                    }
                )
                val merged = statsMap.toMutableMap()
                for (stat in result) merged[stat.turn] = stat
                statsMap = merged
                if (g.id.isNotBlank()) {
                    LibraryStore.updateStatsCache(
                        context, g.id, ReviewMath.encodeSparse(merged)
                    )
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

    // Territory (ownership) overlay for the position at the
    // current ply, computed on demand with a one-off analysis.
    var territory by remember { mutableStateOf<DoubleArray?>(null) }
    var territoryPly by remember { mutableIntStateOf(-1) }
    var territoryBusy by remember { mutableStateOf(false) }
    // Per-ply cache for this game: revisiting a position shows
    // its territory instantly instead of recomputing it.
    var territoryCache by remember {
        mutableStateOf<Map<Int, DoubleArray>>(emptyMap())
    }

    LaunchedEffect(current.id) {
        territoryCache = emptyMap()
        territory = null
        territoryPly = -1
    }

    fun toggleTerritory() {
        if (territoryPly == ply && territory != null) {
            territory = null
            territoryPly = -1
            return
        }
        if (territoryBusy || analyzing) return
        val cached = territoryCache[ply]
        if (cached != null) {
            territory = cached
            territoryPly = ply
            return
        }
        territoryBusy = true
        val g = current
        val atPly = ply
        runner.execute {
            var eng: AnalysisEngine? = null
            try {
                eng = AnalysisEngine.start(
                    context.applicationInfo.nativeLibraryDir,
                    EngineAssets.engineWorkDir(context),
                    ModelStore.resolveActiveFile(context)
                )
                val initPlayer = when {
                    g.customStones.isNotEmpty() -> g.startPlayer
                    g.handicap > 0 -> Stone.WHITE
                    else -> Stone.BLACK
                }
                val res = eng.analyze(
                    size = g.size,
                    komi = g.komi,
                    initialStones = setupStonesOf(g),
                    initialPlayer = initPlayer,
                    moves = g.moves.take(atPly),
                    sideToMove = if (atPly == 0) {
                        initPlayer
                    } else {
                        g.moves[atPly - 1].stone.other()
                    },
                    maxVisits = 300
                )
                territory = res.ownershipBlack
                territoryPly = atPly
                territoryCache = territoryCache + (atPly to res.ownershipBlack)
            } catch (e: Exception) {
                errorText = "领地分析失败：${e.message ?: "未知错误"}"
            } finally {
                try {
                    eng?.quit()
                } catch (_: Exception) {
                }
                territoryBusy = false
            }
        }
    }

    LaunchedEffect(current.id) {
        // A handful of missing turns fills in automatically; a big gap
        // (a library game with no live-recorded stats) lets the user
        // pick fast vs precise first.
        if (totalMoves > 0 && missingTurns.isNotEmpty() && missingTurns.size <= 30) {
            startAnalysis(60)
        }
    }

    val marks = remember(turns) {
        turns?.let { ReviewMath.marks(current.moves, it) } ?: emptyList()
    }

    fun buildGame(upto: Int): GoGame =
        GoGame(current.size, current.komi).also { g ->
            when {
                current.customStones.isNotEmpty() ->
                    g.loadPosition(current.customStones, current.startPlayer)
                current.handicap > 0 -> g.setupHandicap(current.handicap)
            }
            for (i in 0 until upto.coerceAtMost(current.moves.size)) {
                val m = current.moves[i]
                if (m.isPass) g.pass() else g.play(m.x, m.y)
            }
        }

    val replayGame = remember(ply, current.id) { buildGame(ply) }

    // The move that sealed this game (「绝」), re-derived from the
    // recorded stats with the score-lock rule; its stone carries
    // the calligraphy badge whenever the review is at or past it.
    val decidedTurn = remember(turns, current.id) {
        val t = turns
        if (t == null || t.size != current.moves.size + 1) {
            null
        } else {
            val emptyByTurn = IntArray(t.size)
            for (p in 0..current.moves.size) {
                val g = buildGame(p)
                var c = 0
                for (i in 0 until current.size.area) {
                    if (g.stoneAt(
                            i % current.size.width,
                            i / current.size.width
                        ) == null
                    ) {
                        c++
                    }
                }
                emptyByTurn[p] = c
            }
            ReviewMath.firstDecidedTurn(t, emptyByTurn)
        }
    }

    fun enterBranch() {
        branchGame = buildGame(ply)
        branchBasePly = ply
        branchPending = null
        branchResult = null
        branchInfo = "试下：点棋盘落子（点两下确认），可悔棋、停一手；" +
            "点「分析这条分支」看胜率和 AI 一选。主线棋谱不受影响。"
    }

    fun exitBranch() {
        branchGame = null
        branchPending = null
        branchResult = null
        branchInfo = ""
    }

    fun branchAnalyze() {
        val bg = branchGame ?: return
        if (branchBusy) return
        branchBusy = true
        branchInfo = "正在分析这条分支…"
        val movesSnapshot = bg.moves.toList()
        val sideSnapshot = bg.current
        runner.execute {
            var eng: AnalysisEngine? = null
            try {
                eng = AnalysisEngine.start(
                    context.applicationInfo.nativeLibraryDir,
                    EngineAssets.engineWorkDir(context),
                    ModelStore.resolveActiveFile(context)
                )
                val initPlayer = when {
                    current.customStones.isNotEmpty() -> current.startPlayer
                    current.handicap > 0 -> Stone.WHITE
                    else -> Stone.BLACK
                }
                val res = eng.analyze(
                    size = current.size,
                    komi = current.komi,
                    initialStones = setupStonesOf(current),
                    initialPlayer = initPlayer,
                    moves = movesSnapshot,
                    sideToMove = sideSnapshot,
                    maxVisits = 300
                )
                branchResult = res
                val top = res.candidates.firstOrNull()
                val topText = when {
                    top == null -> "无候选"
                    top.move == null -> "停一手"
                    else -> top.vertex
                }
                branchInfo = "分支局面：黑胜率 " +
                    String.format("%.1f%%", res.rootWinrateBlack * 100) +
                    " · 目差 " + String.format("%+.1f", res.rootScoreLeadBlack) +
                    " · AI 一选：$topText"
            } catch (e: Exception) {
                branchInfo = "分支分析失败：${e.message ?: "未知错误"}"
            } finally {
                try {
                    eng?.quit()
                } catch (_: Exception) {
                }
                branchBusy = false
            }
        }
    }

    val branchMarks: List<CandidateMark> = branchResult?.candidates
        ?.mapIndexedNotNull { idx, c ->
            val mv = c.move ?: return@mapIndexedNotNull null
            CandidateMark(mv.x, mv.y, idx + 1)
        } ?: emptyList()

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
            TextButton(onClick = { requestReviewExit() }) { Text("‹ 返回") }
            Spacer(modifier = Modifier.weight(1f))
            if (current.id.isBlank()) {
                TextButton(onClick = { showSaveDialog = true }) { Text("收藏棋谱") }
            } else if (savedNote) {
                Text("已收藏", fontSize = 13.sp, color = Color.Gray)
            }
        }
        Text(current.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "${current.modeLabel} · ${current.resultText.ifBlank { "无结果记录" }} · 共 $totalMoves 手 · " +
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(current.createdAt)),
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))

        val branch = branchGame
        @Suppress("UNUSED_EXPRESSION")
        branchVersion
        if (branch != null) {
            BoardFrame(framed = !settings.boardFill) {
            BoardView(
                game = branch,
                enabled = !branchBusy,
                skin = BoardSkin.fromId(settings.boardSkin),
                showCoordinates = settings.showCoordinates,
                edgeToEdge = settings.boardFill,
                parentPadding = 12.dp,
                territoryThreshold = 0.5,
                candidates = branchMarks,
                ownership = null,
                pvGhost = emptyList(),
                preview = branchPending,
                markedDead = emptySet(),
                flipped = perspective == Stone.WHITE,
                onPointTap = { x, y ->
                    if (branch.isLegal(x, y)) {
                        if (branchPending == Pair(x, y)) {
                            branch.play(x, y)
                            branchPending = null
                            branchResult = null
                            branchVersion++
                        } else {
                            branchPending = Pair(x, y)
                        }
                    }
                }
            )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "试下中（从第 $branchBasePly 手分叉，已试 ${branch.moves.size - branchBasePly} 手）",
                    fontSize = 13.sp, color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        PositionShare.share(
                            context, branch,
                            "试下 · 第 $branchBasePly 手起"
                        )
                    }
                ) { Text("分享局面") }
            }
            if (branchInfo.isNotEmpty()) {
                Text(branchInfo, fontSize = 13.sp, color = Color(0xFF2F7CF6))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { branchAnalyze() },
                    enabled = !branchBusy,
                    modifier = Modifier.weight(1f)
                ) { Text(if (branchBusy) "分析中…" else "分析这条分支") }
                OutlinedButton(
                    onClick = {
                        if (branch.moves.size > branchBasePly) {
                            branch.undo()
                            branchPending = null
                            branchResult = null
                            branchVersion++
                        }
                    },
                    enabled = branch.moves.size > branchBasePly,
                    modifier = Modifier.weight(1f)
                ) { Text("悔棋") }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        branch.pass()
                        branchPending = null
                        branchResult = null
                        branchVersion++
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("停一手") }
                OutlinedButton(
                    onClick = { showBranchSave = true },
                    modifier = Modifier.weight(1f)
                ) { Text("存为棋谱") }
                OutlinedButton(
                    onClick = { exitBranch() },
                    modifier = Modifier.weight(1f)
                ) { Text("退出试下") }
            }
            if (showBranchSave) {
                SaveGameDialog(
                    game = current.copy(
                        id = "",
                        folderId = null,
                        title = current.title + "（分支）",
                        createdAt = System.currentTimeMillis(),
                        moves = branch.moves.toList(),
                        resultText = "",
                        statsCache = ""
                    ),
                    onDismiss = { showBranchSave = false },
                    onSaved = {
                        showBranchSave = false
                        branchInfo = "分支已存入棋谱库"
                    }
                )
            }
        } else {
            BoardFrame(framed = !settings.boardFill) {
            BoardView(
                game = replayGame,
                enabled = false,
                skin = BoardSkin.fromId(settings.boardSkin),
                showCoordinates = settings.showCoordinates,
                edgeToEdge = settings.boardFill,
                parentPadding = 12.dp,
                territoryThreshold = 0.5,
                candidates = bestMark,
                ownership = if (territoryPly == ply) territory else null,
                territorySpread = settings.territorySpread,
                territoryIntensity = settings.territoryIntensity,
                pvGhost = if (ply < totalMoves) {
                    turns?.getOrNull(ply)?.let { st ->
                        ReviewMath.parsePv(st.bestPv, current.size, replayGame.current)
                            .take(7)
                    } ?: emptyList()
                } else {
                    emptyList()
                },
                preview = null,
                markedDead = emptySet(),
                decidedStone = if (decidedTurn != null && ply >= decidedTurn) {
                    current.moves.getOrNull(decidedTurn - 1)
                        ?.takeIf { !it.isPass }
                } else {
                    null
                },
                flipped = perspective == Stone.WHITE
            ) { _, _ -> }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Row {
                RepeatStepButton(
                    text = "上一手",
                    enabled = ply > 0,
                    onStep = { autoPlaying = false; ply = (ply - 1).coerceAtLeast(0) }
                )
                Text(
                    "第 $ply / $totalMoves 手",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp, start = 10.dp)
                )
                RepeatStepButton(
                    text = "下一手",
                    enabled = ply < totalMoves,
                    onStep = { autoPlaying = false; ply = (ply + 1).coerceAtMost(totalMoves) }
                )
            }
            Slider(
                value = ply.toFloat(),
                onValueChange = { autoPlaying = false; ply = it.roundToInt() },
                valueRange = 0f..totalMoves.coerceAtLeast(1).toFloat()
            )
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { autoPlaying = !autoPlaying },
                    modifier = Modifier.weight(1f)
                ) { Text(if (autoPlaying) "暂停" else "自动播放") }
                for ((ms, label) in listOf(600 to "0.6 秒", 1200 to "1.2 秒", 2000 to "2.0 秒")) {
                    FilterChip(
                        selected = autoSpeedMs == ms,
                        onClick = {
                            autoSpeedMs = ms
                            reviewPrefs.edit().putInt("autoMs", ms).apply()
                        },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { enterBranch() },
                    modifier = Modifier.weight(1f)
                ) { Text("试下") }
                OutlinedButton(
                    onClick = {
                        val stat = turns?.getOrNull(ply)
                        val cap = if (stat != null) {
                            "复盘 · 第 $ply / $totalMoves 手 · 黑胜率 " +
                                String.format("%.0f%%", stat.winrateBlack * 100)
                        } else {
                            "复盘 · 第 $ply / $totalMoves 手"
                        }
                        PositionShare.share(context, replayGame, cap)
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("分享局面") }
            }
            Row {
                TextButton(
                    onClick = {
                        Thread {
                            val ok = PositionShare.saveToGallery(
                                context, replayGame,
                                "复盘 · 第 $ply / $totalMoves 手"
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
            if (marks.isNotEmpty()) {
                val markPlys = marks.map { it.moveNumber }.distinct().sorted()
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            markPlys.lastOrNull { it < ply }?.let { ply = it }
                        },
                        enabled = markPlys.any { it < ply },
                        modifier = Modifier.weight(1f)
                    ) { Text("‹ 上个关键点") }
                    OutlinedButton(
                        onClick = {
                            markPlys.firstOrNull { it > ply }?.let { ply = it }
                        },
                        enabled = markPlys.any { it > ply },
                        modifier = Modifier.weight(1f)
                    ) { Text("下个关键点 ›") }
                }
            }
        }

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
                    if (!best?.bestPv.isNullOrBlank() && ply < totalMoves) {
                        Text(
                            "棋盘上的虚影是 AI 一选之后的推荐变化",
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                }
                if (turns?.getOrNull(ply + 1)?.deliberate == true) {
                    Text(
                        "这一手是 AI 故意失误：稳赢时故意放的，已专门标记，不算恶手",
                        fontSize = 13.sp, color = Color(0xFFEF6C00)
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
            Spacer(modifier = Modifier.padding(start = 8.dp))
            FilterChip(
                selected = showLead,
                onClick = { showLead = !showLead },
                label = { Text("显示目差") }
            )
        }
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            FilterChip(
                selected = territoryPly == ply && territory != null,
                onClick = { toggleTerritory() },
                label = {
                    Text(
                        when {
                            territoryBusy -> "领地计算中…"
                            territoryPly == ply && territory != null -> "隐藏领地"
                            else -> "看这步的领地"
                        }
                    )
                },
                enabled = !territoryBusy && !analyzing
            )
            if (territoryPly == ply && territory != null) {
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    "第 $ply 手时的地盘归属（按你的判断设置着色）",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        }
        if (territory != null && territoryPly == ply) {
            // The same ownership also yields a counted score for
            // this exact position — no extra engine work, just the
            // app's usual dead-stone estimate + Chinese scoring.
            val own = territory!!
            val stonesArr = Array(current.size.area) { i ->
                replayGame.stoneAt(
                    i % current.size.width, i / current.size.width
                )
            }
            val dead = TerritoryEstimate.deadStones(
                own, stonesArr, current.size.width, current.size.height,
                SettingsStore.deadGroupMean(settings.deadStrictness)
            )
            val s = replayGame.scoreWithDead(dead)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "这手的形势判断：黑 活子 ${s.blackStones} + 空 " +
                    "${s.blackTerritory} · 白 活子 ${s.whiteStones} + 空 " +
                    "${s.whiteTerritory} + 贴目 ${fmtNum(s.komi)} → " +
                    if (s.lead > 0) {
                        "黑领先约 ${fmtNum(s.lead)} 目"
                    } else {
                        "白领先约 ${fmtNum(-s.lead)} 目"
                    },
                fontSize = 12.sp, color = Color.Gray
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
                    showLead = showLead,
                    onSeek = { autoPlaying = false; scrubTo(it) }
                )
                ChartLegend(
                    showDeliberate = marks.any { it.kind == MarkKind.DELIBERATE }
                )
                val summary = ReviewMath.gameSummary(
                    current.moves, turns!!, current.size
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    GameCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "整局讲评",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (summary.turningPoint > 0) {
                                val tpStone = current.moves[
                                    summary.turningPoint - 1
                                ].stone
                                Text(
                                    text = "转折点：第 ${summary.turningPoint} 手" +
                                        "（${if (tpStone == Stone.BLACK) "黑" else "白"}），" +
                                        "黑方胜率 ${fmtPct(summary.turningBeforeBlack)}" +
                                        " → ${fmtPct(summary.turningAfterBlack)}",
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (summary.blunders.isEmpty()) {
                                Text(
                                    text = "全场没有大恶手，双方发挥稳定。",
                                    fontSize = 13.sp, color = Color.Gray
                                )
                            } else {
                                for (b in summary.blunders) {
                                    Text(
                                        text = "恶手：第 ${b.moveNumber} 手" +
                                            "（${if (b.stone == Stone.BLACK) "黑" else "白"}）" +
                                            "胜率 −${fmtNum(b.dropPercent)}%" +
                                            if (b.bestText.isNotEmpty()) {
                                                "，AI 会下 ${b.bestText}"
                                            } else {
                                                ""
                                            } + "（点此跳转）",
                                        fontSize = 13.sp,
                                        color = Color(0xFFC62828),
                                        modifier = Modifier
                                            .clickable {
                                                autoPlaying = false
                                                scrubTo(b.moveNumber - 1)
                                            }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                            for (dm in summary.deliberateMoves) {
                                Text(
                                    text = "AI 故意失误：第 $dm 手" +
                                        "（${if (current.moves[dm - 1].stone == Stone.BLACK) "黑" else "白"}" +
                                        "）——这一手是 AI 稳赢时故意放的，不算恶手（点此跳转）",
                                    fontSize = 13.sp,
                                    color = Color(0xFFEF6C00),
                                    modifier = Modifier
                                        .clickable {
                                            autoPlaying = false
                                            scrubTo(dm - 1)
                                        }
                                        .padding(vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "全场统计：黑方恶手 ${summary.blackBlunders} 次" +
                                    " · 白方恶手 ${summary.whiteBlunders} 次" +
                                    " · 妙手 ${summary.brilliantCount} 次",
                                fontSize = 12.sp, color = Color.Gray
                            )
                        }
                    }
                }
            }
            analyzing -> {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("正在补算缺失的手数…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                            "对局时已算好 ${statsMap.size} 手，只需补算 $progressTotal 手；已完成 $progressDone 手。补完会自动保存，下次打开直接看。",
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                }
            }
            errorText.isNotEmpty() -> {
                Text(errorText, fontSize = 13.sp, color = Color(0xFFB3261E))
                OutlinedButton(onClick = { startAnalysis(60) }) { Text("重新分析") }
            }
            missingTurns.size > 30 -> {
                GameCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("这局棋还没有逐手数据", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "共需分析 ${missingTurns.size} 手（以后新打的对局会在对局时就偷偷算好，复盘秒开）。选一种分析方式：",
                            fontSize = 12.sp, color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { startAnalysis(40) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("快速分析（够看恶手妙手）") }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { startAnalysis(100) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("精准分析（更慢，数字更准）") }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { startAnalysis(250) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("高精度分析（最慢，胜率和目差最可靠）") }
                    }
                }
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
                    MarkKind.DELIBERATE -> "故意失误" to Color(0xFFEF6C00)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp, end = 6.dp)
                            .size(10.dp)
                            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                    )
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
                "判定：胜率掉 15% 以上为恶手；下出 AI 一选、不亏，且这一选明显甩开其他候选（胜率拉开 5% 以上）才算妙手；全场胜率波动最大的一手为关键手。",
                fontSize = 11.sp, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val t = turns!!
                    val comments = HashMap<Int, String>()
                    for (i in current.moves.indices) {
                        val after = t[i + 1]
                        val sb = StringBuilder()
                        sb.append("黑胜率 ").append(fmtPct(after.winrateBlack))
                        sb.append(" · 黑目差 ").append(String.format("%+.1f", after.scoreLeadBlack))
                        for (mark in marks) {
                            if (mark.moveNumber == i + 1) {
                                val label = when (mark.kind) {
                                    MarkKind.BLUNDER -> "恶手"
                                    MarkKind.BRILLIANT -> "妙手"
                                    MarkKind.KEY -> "关键手"
                                    MarkKind.DELIBERATE -> "AI 故意失误"
                                }
                                sb.append(" 【").append(label).append(" ")
                                    .append(fmtDelta(mark.deltaPercent)).append("】")
                            }
                        }
                        comments[i + 1] = sb.toString()
                    }
                    val sgf = com.tuxungo.android.go.Sgf.export(
                        game = buildGame(totalMoves),
                        setupStones = current.customStones,
                        startPlayer = current.startPlayer,
                        comments = comments
                    )
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(sgf))
                    sgfCopied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (sgfCopied) "已复制，去粘贴分享吧" else "导出带解说棋谱（复制 SGF）") }
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

    if (showBranchExitConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBranchExitConfirm = false },
            title = { Text("退出复盘？") },
            text = {
                Text("试下分支还没保存，退出会丢失这条分支。确定退出吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showBranchExitConfirm = false
                    onBack()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showBranchExitConfirm = false }) {
                    Text("继续复盘")
                }
            }
        )
    }

    if (showSaveDialog) {
        SaveGameDialog(
            game = current.copy(
                statsCache = if (statsMap.isNotEmpty()) {
                    ReviewMath.encodeSparse(statsMap)
                } else {
                    current.statsCache
                }
            ),
            onDismiss = { showSaveDialog = false },
            onSaved = { saved ->
                current = saved
                savedNote = true
                showSaveDialog = false
            }
        )
    }
}

@Composable
fun WinrateChart(
    turns: List<TurnStat>,
    perspective: Stone,
    ply: Int,
    marks: List<MoveMark>,
    showLead: Boolean = true,
    winSide: Stone? = null,
    onSeek: (Int) -> Unit
) {
    val total = (turns.size - 1).coerceAtLeast(1)
    val winColor = Color(0xFFB03A2E)
    val leadColor = Color(0xFFE6A817)
    val greenFill = Color(0xFF43A047).copy(alpha = 0.30f)
    val gridColor = Color.Gray.copy(alpha = 0.35f)
    val cursorColor = Color(0xFF6A1B9A)
    // Single-side mode (winSide != null, used by the in-game trend
    // dialog since 2.64.0): only that side's win-rate line is drawn
    // and all win-rate geometry follows it; [perspective] steers
    // the lead line. Review keeps winSide null (red line, caller's
    // perspective).
    val wrPerspective = winSide ?: perspective

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
                path.lineTo(xOf(t), yWr(ReviewMath.winrateFor(turns[t], wrPerspective)))
            }
            path.lineTo(xOf(endTurn), yWr(0.75))
            path.close()
            drawPath(path, greenFill)
            runStart = -1
        }
        for (t in turns.indices) {
            val above = ReviewMath.winrateFor(turns[t], wrPerspective) >= 0.75
            if (above && runStart < 0) runStart = t
            if (!above && runStart >= 0) flushRun(t - 1)
        }
        if (runStart >= 0) flushRun(turns.size - 1)

        // Score-lead line (perspective-adjusted).
        if (showLead) {
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
        }

        // Win-rate line. Single-side mode draws only the chosen
        // side's line (Black dark, White with a grey halo), with
        // dots where it crosses 50/50.
        if (winSide != null) {
            val sidePath = Path()
            for (t in turns.indices) {
                val x = xOf(t)
                val y = yWr(ReviewMath.winrateFor(turns[t], winSide))
                if (t == 0) sidePath.moveTo(x, y) else sidePath.lineTo(x, y)
            }
            if (winSide == Stone.WHITE) {
                drawPath(
                    sidePath, Color(0xFF78909C).copy(alpha = 0.65f),
                    style = Stroke(width = 5.5f)
                )
                drawPath(sidePath, Color.White, style = Stroke(width = 3f))
            } else {
                drawPath(sidePath, Color(0xFF212121), style = Stroke(width = 3f))
            }
            val series = turns.map { ReviewMath.winrateFor(it, winSide) }
            for (ci in AnalysisMath.winrateCrossings(series)) {
                drawCircle(
                    Color(0xFF2E7D32), radius = 6f,
                    center = Offset(xOf(ci), h / 2f)
                )
                drawCircle(
                    Color.White, radius = 2.6f,
                    center = Offset(xOf(ci), h / 2f)
                )
            }
        } else {
            val wrPath = Path()
            for (t in turns.indices) {
                val x = xOf(t)
                val y = yWr(ReviewMath.winrateFor(turns[t], perspective))
                if (t == 0) wrPath.moveTo(x, y) else wrPath.lineTo(x, y)
            }
            drawPath(wrPath, winColor, style = Stroke(width = 3f))
        }

        // Mark dots.
        for (mark in marks) {
            val t = mark.moveNumber.coerceIn(0, total)
            val center = Offset(
                xOf(t),
                yWr(ReviewMath.winrateFor(turns[t], wrPerspective))
            )
            val (color, radius) = when (mark.kind) {
                MarkKind.BLUNDER -> Color(0xFFC62828) to 6f
                MarkKind.BRILLIANT -> Color(0xFF2E7D32) to 6f
                MarkKind.KEY -> Color(0xFF6A1B9A) to 8f
                MarkKind.DELIBERATE -> Color(0xFFEF6C00) to 6f
            }
            drawCircle(color = Color.White, radius = radius + 2f, center = center)
            drawCircle(color = color, radius = radius, center = center)
        }

        // Cursor: a line plus a ringed handle on the curve, easy to
        // grab and drag along.
        val cx = xOf(ply.coerceIn(0, total))
        drawLine(cursorColor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 3f)
        val handle = Offset(
            cx,
            yWr(ReviewMath.winrateFor(turns[ply.coerceIn(0, total)], wrPerspective))
        )
        drawCircle(color = Color.White, radius = 10f, center = handle)
        drawCircle(color = cursorColor, radius = 7f, center = handle)
    }
}

/** Graphic legend for the chart: swatches first, words second. */
@Composable
private fun ChartLegend(showDeliberate: Boolean = false) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 3.dp)
                .background(Color(0xFFB03A2E))
        )
        Text(" 胜率  ", fontSize = 11.sp, color = Color.Gray)
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 3.dp)
                .background(Color(0xFFE6A817))
        )
        Text(" 目差  ", fontSize = 11.sp, color = Color.Gray)
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(0xFF43A047).copy(alpha = 0.40f))
        )
        Text(" 优势区 ≥75%  ", fontSize = 11.sp, color = Color.Gray)
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color(0xFF6A1B9A), shape = androidx.compose.foundation.shape.CircleShape)
        )
        Text(" 当前手", fontSize = 11.sp, color = Color.Gray)
        if (showDeliberate) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(10.dp)
                    .background(Color(0xFFEF6C00), shape = androidx.compose.foundation.shape.CircleShape)
            )
            Text(" AI 故意失误", fontSize = 11.sp, color = Color.Gray)
        }
    }
    Text(
        "按住曲线左右拖动，可以跳到任意一手",
        fontSize = 11.sp, color = Color.Gray
    )
}

private fun fmtPct(v: Double): String = String.format("%.1f%%", v * 100)

/**
 * A step button that keeps stepping while held: one step on tap,
 * then auto-repeat after a short delay.
 */
@Composable
private fun RepeatStepButton(
    text: String,
    enabled: Boolean,
    onStep: () -> Unit
) {
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding, enabled) {
        if (holding && enabled) {
            kotlinx.coroutines.delay(380)
            while (true) {
                onStep()
                kotlinx.coroutines.delay(90)
            }
        }
    }
    Button(
        onClick = onStep,
        enabled = enabled,
        modifier = Modifier.pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    holding = true
                    tryAwaitRelease()
                    holding = false
                }
            )
        }
    ) { Text(text) }
}

private fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)

private fun fmtDelta(v: Double): String =
    String.format("%+.1f%%", v)
