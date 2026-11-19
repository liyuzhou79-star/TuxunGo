package com.tuxungo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tuxungo.android.ui.GameConfig
import com.tuxungo.android.ui.GameScreen
import com.tuxungo.android.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TuxunGoApp()
                }
            }
        }
    }
}

@Composable
fun TuxunGoApp() {
    var config by remember { mutableStateOf<GameConfig?>(null) }
    val current = config
    if (current == null) {
        SetupScreen(onStart = { config = it })
    } else {
        key(current) {
            GameScreen(config = current, onExit = { config = null })
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    MaterialTheme {
        TuxunGoApp()
    }
}
