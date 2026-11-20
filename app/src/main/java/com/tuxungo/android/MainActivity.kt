package com.tuxungo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
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
                    TuxunGoApp(onAccentChange = { accent = it })
                }
            }
        }
    }
}

private enum class Screen { SETUP, MODELS, SETTINGS, EDIT, GAME, LIBRARY, REVIEW, GUESS, TSUMEGO, STATS }

@Composable
fun TuxunGoApp(onAccentChange: (String) -> Unit = {}) {
    val context = LocalContext.current
    // Restore an unfinished game after backgrounding / process death.
    val restored = remember {
        GameStore.load(context)?.takeIf { !it.finished }
    }
    var screen by remember { mutableStateOf(if (restored != null) Screen.GAME else Screen.SETUP) }
    var config by remember { mutableStateOf(restored?.config) }
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
            onOpenModels = { screen = Screen.MODELS },
            onOpenLibrary = { screen = Screen.LIBRARY },
            onOpenGuess = { screen = Screen.GUESS },
            onOpenTsumego = { screen = Screen.TSUMEGO },
            onOpenStats = { screen = Screen.STATS },
            onOpenGame = {
                reviewGame = it
                reviewInitialPly = null
                screenBeforeReview = Screen.SETUP
                screen = Screen.REVIEW
            },
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenEditor = { w, h ->
                editSize = w to h
                screen = Screen.EDIT
            }
        )
        Screen.MODELS -> ModelsScreen(onBack = { screen = Screen.SETUP })
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
            onBack = { screen = Screen.SETUP }
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
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    MaterialTheme(colorScheme = SakuraColors) {
        TuxunGoApp()
    }
}
