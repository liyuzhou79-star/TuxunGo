package com.tuxungo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "TuxunGo", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "免费开源的围棋对弈与 AI 分析应用，内置 KataGo 引擎。",
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        BoardPreview()
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "人对 AI · AI 对 AI · 人对人", fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "版本 0.1.0（开发中）", fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable
fun BoardPreview() {
    val lineColor = Color(0xFF8A6D3B)
    val boardColor = Color(0xFFE8C07A)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        drawRect(color = boardColor)
        val lines = 19
        val step = size.width / (lines + 1)
        for (i in 1..lines) {
            val p = step * i
            drawLine(lineColor, Offset(p, step), Offset(p, size.height - step), strokeWidth = 2f)
            drawLine(lineColor, Offset(step, p), Offset(size.width - step, p), strokeWidth = 2f)
        }
        // Two stones and the blue first-choice marker, echoing the app icon.
        drawCircle(Color.Black, radius = step * 0.42f, center = Offset(step * 4, step * 4))
        drawCircle(Color.White, radius = step * 0.42f, center = Offset(step * 16, step * 16))
        drawCircle(
            Color(0xFF2F7CF6),
            radius = step * 0.5f,
            center = Offset(step * 10, step * 10),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    MaterialTheme {
        HomeScreen()
    }
}
