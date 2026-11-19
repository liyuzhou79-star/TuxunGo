package com.tuxungo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.tuxungo.android.ui.ModelsScreen
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = SakuraColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TuxunGoApp()
                }
            }
        }
    }
}

private enum class Screen { SETUP, MODELS, SETTINGS, EDIT, GAME }

@Composable
fun TuxunGoApp() {
    val context = LocalContext.current
    // Restore an unfinished game after backgrounding / process death.
    val restored = remember {
        GameStore.load(context)?.takeIf { !it.finished }
    }
    var screen by remember { mutableStateOf(if (restored != null) Screen.GAME else Screen.SETUP) }
    var config by remember { mutableStateOf(restored?.config) }
    var restoreMoves by remember { mutableStateOf<List<Move>>(restored?.moves ?: emptyList()) }
    var editSize by remember { mutableStateOf(19 to 19) }
    when (screen) {
        Screen.SETUP -> SetupScreen(
            onStart = {
                config = it
                restoreMoves = emptyList()
                screen = Screen.GAME
            },
            onOpenModels = { screen = Screen.MODELS },
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenEditor = { w, h ->
                editSize = w to h
                screen = Screen.EDIT
            }
        )
        Screen.MODELS -> ModelsScreen(onBack = { screen = Screen.SETUP })
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.SETUP })
        Screen.EDIT -> EditScreen(
            width = editSize.first,
            height = editSize.second,
            onStart = {
                config = it
                restoreMoves = emptyList()
                screen = Screen.GAME
            },
            onBack = { screen = Screen.SETUP }
        )
        Screen.GAME -> {
            val current = config
            if (current == null) {
                screen = Screen.SETUP
            } else {
                key(current, restoreMoves) {
                    GameScreen(
                        config = current,
                        initialMoves = restoreMoves,
                        onExit = { screen = Screen.SETUP }
                    )
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
