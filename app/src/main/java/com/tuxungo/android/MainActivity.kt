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
import androidx.compose.ui.tooling.preview.Preview
import com.tuxungo.android.ui.GameConfig
import com.tuxungo.android.ui.GameScreen
import com.tuxungo.android.ui.ModelsScreen
import com.tuxungo.android.ui.SetupScreen

/** Warm paper-and-walnut palette, echoing the wooden board. */
private val WarmColors = lightColorScheme(
    primary = Color(0xFF7A4E21),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0DFC2),
    onPrimaryContainer = Color(0xFF3E2708),
    secondary = Color(0xFF2456C8),
    onSecondary = Color.White,
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF2B2118),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF2B2118),
    surfaceVariant = Color(0xFFEEE5D3),
    onSurfaceVariant = Color(0xFF5C4A33),
    outline = Color(0xFFB49B78)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = WarmColors) {
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

private enum class Screen { SETUP, MODELS, GAME }

@Composable
fun TuxunGoApp() {
    var screen by remember { mutableStateOf(Screen.SETUP) }
    var config by remember { mutableStateOf<GameConfig?>(null) }
    when (screen) {
        Screen.SETUP -> SetupScreen(
            onStart = {
                config = it
                screen = Screen.GAME
            },
            onOpenModels = { screen = Screen.MODELS }
        )
        Screen.MODELS -> ModelsScreen(onBack = { screen = Screen.SETUP })
        Screen.GAME -> {
            val current = config
            if (current == null) {
                screen = Screen.SETUP
            } else {
                key(current) {
                    GameScreen(config = current, onExit = { screen = Screen.SETUP })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    MaterialTheme(colorScheme = WarmColors) {
        TuxunGoApp()
    }
}
