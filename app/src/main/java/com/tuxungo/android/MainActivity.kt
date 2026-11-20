package com.tuxungo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.tuxungo.android.go.Move
import com.tuxungo.android.ui.EditScreen
import com.tuxungo.android.ui.GameConfig
import com.tuxungo.android.ui.GameScreen
import com.tuxungo.android.ui.LibraryScreen
import com.tuxungo.android.ui.ModelsScreen
import com.tuxungo.android.ui.OnboardingScreen
import com.tuxungo.android.ui.PhysicalClockScreen
import com.tuxungo.android.ui.ReviewScreen
import com.tuxungo.android.ui.SettingsScreen
import com.tuxungo.android.ui.SetupScreen

/** Sakura-pink palette. */
private val SakuraColors = lightColorScheme(
    primary = Color(0xFFD64F7C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBDCE7),
    onPrimaryContainer = Color(0xFF5E1230),
    secondary = Color(0xFFB03A5F),
    onSecondary = Color.White,
    background = Color(0xFFFDF5F7),
    onBackground = Color(0xFF33222A),
    surface = Color(0xFFFFFDFE),
    onSurface = Color(0xFF33222A),
    surfaceVariant = Color(0xFFF7E3EA),
    onSurfaceVariant = Color(0xFF6E4A58),
    outline = Color(0xFFD9A7B8),
    error = Color(0xFFC62828),
    onError = Color.White
)

/** Matcha-green palette. */
private val MatchaColors = lightColorScheme(
    primary = Color(0xFF5B8C5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBD8),
    onPrimaryContainer = Color(0xFF1E3A1E),
    secondary = Color(0xFF3E6B3E),
    onSecondary = Color.White,
    background = Color(0xFFF6F9F3),
    onBackground = Color(0xFF22301F),
    surface = Color(0xFFFEFFFE),
    onSurface = Color(0xFF22301F),
    surfaceVariant = Color(0xFFE3EDDC),
    onSurfaceVariant = Color(0xFF4A5E46),
    outline = Color(0xFFA7BFA0),
    error = Color(0xFFC62828),
    onError = Color.White
)

/** Indigo palette. */
private val IndigoColors = lightColorScheme(
    primary = Color(0xFF4A5DA9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3F7),
    onPrimaryContainer = Color(0xFF1B2450),
    secondary = Color(0xFF35477F),
    onSecondary = Color.White,
    background = Color(0xFFF5F6FC),
    onBackground = Color(0xFF23273A),
    surface = Color(0xFFFEFEFF),
    onSurface = Color(0xFF23273A),
    surfaceVariant = Color(0xFFE1E6F5),
    onSurfaceVariant = Color(0xFF4A5170),
    outline = Color(0xFFA3AED6),
    error = Color(0xFFC62828),
    onError = Color.White
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen gesture navigation: draw edge to edge with
        // transparent system bars (dark icons — the app is always
        // light-themed), and pad the content by the safe-drawing
        // insets below so nothing hides under the status bar, the
        // gesture bar or a display cutout.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        // Idle pre-warm: a couple of seconds after launch, start
        // the analysis engine on a background thread so the first
        // in-game analysis / judgment feels instant. The game
        // screen adopts this engine instead of starting its own.
        Thread {
            try {
                Thread.sleep(2500)
                val appCtx = applicationContext
                val model = com.tuxungo.android.engine.ModelStore
                    .resolveActiveFile(appCtx)
                val eng = com.tuxungo.android.engine.AnalysisEngine.start(
                    nativeLibDir = applicationInfo.nativeLibraryDir,
                    workDir = com.tuxungo.android.engine.EngineAssets
                        .engineWorkDir(appCtx),
                    modelFile = model,
                    humanModelFile = com.tuxungo.android.engine.ModelStore
                        .humanModelFile(appCtx)
                )
                com.tuxungo.android.engine.AnalysisEngineHolder.offer(eng)
            } catch (_: Exception) {
                // Pre-warm is best-effort; the game screen starts
                // its own engine when this one is missing.
            }
        }.start()
        setContent {
            var accent by remember {
                mutableStateOf(SettingsStore.load(this@MainActivity).accent)
            }
            val colors = when (accent) {
                "matcha" -> MatchaColors
                "indigo" -> IndigoColors
                else -> SakuraColors
            }
            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // The Surface stays full-bleed so the theme
                    // background flows behind the transparent system
                    // bars; only the content is inset to the safe area.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        TuxunGoApp(
                            onAccentChange = { accent = it },
                            shortcut = intent?.getStringExtra("shortcut")
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { SETUP, MODELS, SETTINGS, EDIT, GAME, LIBRARY, REVIEW, GUESS, TSUMEGO, STATS, PHYSICAL_CLOCK, TRAINING, TOOLS }

@Composable
fun TuxunGoApp(
    onAccentChange: (String) -> Unit = {},
    shortcut: String? = null
) {
    val context = LocalContext.current
    // Version guide: shown the first time the app opens after an
    // install or an update, re-openable from the home footer.
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    val metaPrefs = remember {
        context.getSharedPreferences("app_meta", android.content.Context.MODE_PRIVATE)
    }
    val seenVersion = remember { metaPrefs.getString("seen_version", "") ?: "" }
    var showGuide by remember { mutableStateOf(seenVersion != versionName) }
    val guideIsUpdate = seenVersion.isNotEmpty()
    // Restore an unfinished game after backgrounding / process death.
    val restored = remember {
        GameStore.load(context)?.takeIf { !it.finished }
    }
    var screen by remember {
        mutableStateOf(
            when {
                shortcut == "guess" -> Screen.GUESS
                shortcut == "tsumego" -> Screen.TSUMEGO
                restored != null -> Screen.GAME
                shortcut == "quick" -> Screen.GAME
                else -> Screen.SETUP
            }
        )
    }
    var config by remember {
        mutableStateOf(
            restored?.config
                ?: if (shortcut == "quick") {
                    GameConfig(
                        mode = com.tuxungo.android.ui.GameMode.PVE,
                        width = 19, height = 19, aiVisits = 200
                    )
                } else {
                    null
                }
        )
    }
    var restoreMoves by remember { mutableStateOf<List<Move>>(restored?.moves ?: emptyList()) }
    var restoreStats by remember {
        mutableStateOf(restored?.turnStats ?: emptyMap())
    }
    var restoreClock by remember {
        mutableStateOf(restored?.clock)
    }
    var restoreTimeoutBy by remember {
        mutableStateOf(restored?.timeoutBy)
    }
    var restartNonce by remember { mutableIntStateOf(0) }
    var editSize by remember { mutableStateOf(19 to 19) }
    var reviewGame by remember { mutableStateOf<LibraryGame?>(null) }
    var reviewInitialPly by remember { mutableStateOf<Int?>(null) }
    var screenBeforeReview by remember { mutableStateOf(Screen.SETUP) }
    var tsumegoInitial by remember {
        mutableIntStateOf(
            if (shortcut == "tsumego") {
                // Same daily pick as the home card: rotate through
                // the unsolved problems by day of year.
                val prefs = context.getSharedPreferences(
                    "tsumego_prefs", android.content.Context.MODE_PRIVATE
                )
                val solved = prefs.getString("solved", "")
                    ?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet()
                    ?: emptySet()
                val unsolved = com.tuxungo.android.ui.TSUMEGO_PROBLEMS.indices
                    .filter { it !in solved }
                if (unsolved.isEmpty()) {
                    0
                } else {
                    unsolved[
                        java.util.Calendar.getInstance()
                            .get(java.util.Calendar.DAY_OF_YEAR) % unsolved.size
                    ]
                }
            } else {
                0
            }
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
    androidx.compose.animation.AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(220)
            ) + androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(220)
            ) { it / 8 }) togetherWith
                (androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(180)
                ) + androidx.compose.animation.slideOutHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(180)
                ) { -it / 8 })
        },
        label = "screen"
    ) { targetScreen ->
    // System back (button or edge swipe) walks back to the home
    // screen instead of leaving the app. The game and review
    // screens handle their own back (exit confirmation), so
    // they are excluded here.
    androidx.activity.compose.BackHandler(
        enabled = targetScreen != Screen.SETUP &&
            targetScreen != Screen.GAME &&
            targetScreen != Screen.REVIEW
    ) {
        screen = Screen.SETUP
    }

    when (targetScreen) {
        Screen.SETUP -> SetupScreen(
            onStart = {
                config = it
                restoreMoves = emptyList()
                restoreStats = emptyMap()
                restoreClock = null
                restoreTimeoutBy = null
                screen = Screen.GAME
            },
            onOpenTraining = { screen = Screen.TRAINING },
            onOpenTools = { screen = Screen.TOOLS },
            onOpenGame = {
                reviewGame = it
                reviewInitialPly = null
                screenBeforeReview = Screen.SETUP
                screen = Screen.REVIEW
            },
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenGuide = { showGuide = true },
            onOpenEditor = { w, h ->
                editSize = w to h
                screen = Screen.EDIT
            }
        )
        Screen.MODELS -> ModelsScreen(onBack = { screen = Screen.SETUP })
        Screen.PHYSICAL_CLOCK -> PhysicalClockScreen(onBack = { screen = Screen.SETUP })
        Screen.TRAINING -> com.tuxungo.android.ui.TrainingScreen(
            onBack = { screen = Screen.SETUP },
            onOpenGuess = { screen = Screen.GUESS },
            onOpenTsumego = { idx ->
                tsumegoInitial = idx
                screen = Screen.TSUMEGO
            }
        )
        Screen.TOOLS -> com.tuxungo.android.ui.ToolsScreen(
            onBack = { screen = Screen.SETUP },
            onOpenLibrary = { screen = Screen.LIBRARY },
            onOpenStats = { screen = Screen.STATS },
            onOpenModels = { screen = Screen.MODELS },
            onOpenPhysicalClock = { screen = Screen.PHYSICAL_CLOCK }
        )
        Screen.LIBRARY -> LibraryScreen(
            onBack = { screen = Screen.SETUP },
            onOpenGame = {
                reviewGame = it
                reviewInitialPly = null
                screenBeforeReview = Screen.LIBRARY
                screen = Screen.REVIEW
            }
        )
        Screen.REVIEW -> {
            val g = reviewGame
            if (g == null) {
                screen = Screen.SETUP
            } else {
                ReviewScreen(
                    game = g,
                    initialPly = reviewInitialPly ?: -1,
                    onBack = {
                        if (screenBeforeReview == Screen.GAME) {
                            // Rebuild the finished board from its moves.
                            restoreMoves = g.moves
                            restoreStats = com.tuxungo.android.engine.ReviewMath.decodeSparse(g.statsCache)
                            screen = Screen.GAME
                        } else {
                            screen = screenBeforeReview
                        }
                    },
                    onContinue = {
                        config = it
                        restoreMoves = emptyList()
                restoreStats = emptyMap()
                restoreClock = null
                        restoreTimeoutBy = null
                        screen = Screen.GAME
                    }
                )
            }
        }
        Screen.SETTINGS -> SettingsScreen(
            onBack = { screen = Screen.SETUP },
            onAccentChange = onAccentChange
        )
        Screen.GUESS -> com.tuxungo.android.ui.GuessScreen(
            onBack = { screen = Screen.SETUP }
        )
        Screen.TSUMEGO -> com.tuxungo.android.ui.TsumegoScreen(
            onBack = { screen = Screen.SETUP },
            initialIndex = tsumegoInitial
        )
        Screen.STATS -> com.tuxungo.android.ui.StatsScreen(
            onBack = { screen = Screen.SETUP }
        )
        Screen.EDIT -> EditScreen(
            width = editSize.first,
            height = editSize.second,
            onStart = {
                config = it
                restoreMoves = emptyList()
                restoreStats = emptyMap()
                restoreClock = null
                restoreTimeoutBy = null
                screen = Screen.GAME
            },
            onBack = { screen = Screen.SETUP }
        )
        Screen.GAME -> {
            val current = config
            if (current == null) {
                screen = Screen.SETUP
            } else {
                key(current, restoreMoves, restartNonce) {
                    GameScreen(
                        config = current,
                        initialMoves = restoreMoves,
                        onExit = { screen = Screen.SETUP },
                        onRestart = {
                            restoreMoves = emptyList()
                            restoreStats = emptyMap()
                            restoreClock = null
                            restoreTimeoutBy = null
                            restartNonce++
                        },
                        onReview = {
                            reviewGame = it
                            reviewInitialPly = null
                            screenBeforeReview = Screen.GAME
                            screen = Screen.REVIEW
                        },
                        onReviewAtPly = { g, ply ->
                            reviewGame = g
                            reviewInitialPly = ply
                            screenBeforeReview = Screen.GAME
                            screen = Screen.REVIEW
                        },
                        initialStats = restoreStats,
                        initialClock = restoreClock,
                        initialTimeoutBy = restoreTimeoutBy
                    )
                }
            }
        }
    }
    }
    if (showGuide) {
        OnboardingScreen(
            versionName = versionName,
            isUpdate = guideIsUpdate,
            whatsNew = "· 对局里有走势小图了：胜率变化一眼看清，不用等复盘\n" +
                "· AI 表演赛有解说流了：每手一句话，像看直播一样观战\n" +
                "· 定级战报：和定级 AI 下完，结果弹窗里会评估你的大致水平\n" +
                "· 全部数据都复用后台已算好的结果，不重复计算，更快也更省电",
            onFinish = {
                metaPrefs.edit().putString("seen_version", versionName).apply()
                showGuide = false
            }
        )
    }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    MaterialTheme(colorScheme = SakuraColors) {
        TuxunGoApp()
    }
}
