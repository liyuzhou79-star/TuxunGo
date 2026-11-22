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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

/** Sakura-pink palette, dark variant. */
private val SakuraDarkColors = darkColorScheme(
    primary = Color(0xFFF08FAD),
    onPrimary = Color(0xFF4A0E24),
    primaryContainer = Color(0xFF5E2A3C),
    onPrimaryContainer = Color(0xFFFBDCE7),
    secondary = Color(0xFFE5A3B8),
    onSecondary = Color(0xFF3A1424),
    background = Color(0xFF1C1417),
    onBackground = Color(0xFFF3E4E9),
    surface = Color(0xFF261B20),
    onSurface = Color(0xFFF3E4E9),
    surfaceVariant = Color(0xFF3A2A31),
    onSurfaceVariant = Color(0xFFD8B7C2),
    outline = Color(0xFF8A6472),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

/** Matcha-green palette, dark variant. */
private val MatchaDarkColors = darkColorScheme(
    primary = Color(0xFF9CC493),
    onPrimary = Color(0xFF14300F),
    primaryContainer = Color(0xFF2E4A2A),
    onPrimaryContainer = Color(0xFFDCEBD8),
    secondary = Color(0xFFA9C79F),
    onSecondary = Color(0xFF1C2E18),
    background = Color(0xFF141A12),
    onBackground = Color(0xFFE2EDDC),
    surface = Color(0xFF1D251B),
    onSurface = Color(0xFFE2EDDC),
    surfaceVariant = Color(0xFF2E3A2B),
    onSurfaceVariant = Color(0xFFB9CCB2),
    outline = Color(0xFF71856C),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

/** Indigo palette, dark variant. */
private val IndigoDarkColors = darkColorScheme(
    primary = Color(0xFFA9B8F0),
    onPrimary = Color(0xFF16224F),
    primaryContainer = Color(0xFF2E3D75),
    onPrimaryContainer = Color(0xFFDDE3F7),
    secondary = Color(0xFFB4C0EA),
    onSecondary = Color(0xFF1E2A52),
    background = Color(0xFF13141C),
    onBackground = Color(0xFFE1E4F2),
    surface = Color(0xFF1C1E29),
    onSurface = Color(0xFFE1E4F2),
    surfaceVariant = Color(0xFF2B2E3D),
    onSurfaceVariant = Color(0xFFB9BED6),
    outline = Color(0xFF6B7194),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen gesture navigation: draw edge to edge with
        // transparent system bars (icon brightness follows the
        // active light/dark appearance via a SideEffect in the
        // content), and pad the content by the safe-drawing
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
            var themeMode by remember {
                mutableStateOf(SettingsStore.load(this@MainActivity).themeMode)
            }
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val colors = when (accent) {
                "matcha" -> if (dark) MatchaDarkColors else MatchaColors
                "indigo" -> if (dark) IndigoDarkColors else IndigoColors
                else -> if (dark) SakuraDarkColors else SakuraColors
            }
            // System-bar icons follow the active appearance (the
            // bars themselves stay transparent).
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
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
                            onThemeModeChange = { themeMode = it },
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
    onThemeModeChange: (String) -> Unit = {},
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
            // Entering or leaving a game fades only: the sideways
            // slide made starting a game feel like the board was
            // being pushed in from the side, which read as "off".
            // Secondary pages keep the gentle slide.
            if (targetState == Screen.GAME || initialState == Screen.GAME) {
                androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(260)
                ) togetherWith androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(200)
                )
            } else {
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
            }
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
            onAccentChange = onAccentChange,
            onThemeModeChange = onThemeModeChange
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
            whatsNew = "· 分析标记全换新：一选是紫色大圈，二、三选是白色圈，人类选点是橙红色圈；人类一选和 AI 一选撞在同一点时，那个圈会发光——人和 AI 想到一块去了\n· 「绝」按新规矩判：胜率过 99.4%，且对方把剩下的空全占了也翻不了盘，才算定局；定局那一手的子上会留一个书法体的「绝」字，一直陪你下完这盘\n· 局内走势改成双线：黑白双方的胜率各一条线，五五开的交点有专门标记，金色目差线能切换看黑方还是白方\n· 新增「AI 故意失误」（设置 → 对局，或对局里的 AI 面板）：全力的 AI 稳赢时，每局随机 1～2 手故意下出明显的失误——仍保证赢、大龙打架时不放，复盘里会专门标出来，不算恶手\n· 手感打磨：新落的子会轻轻沉一下落稳，分析标记是浮现到棋盘上的，走势展开收起有了动画",
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
