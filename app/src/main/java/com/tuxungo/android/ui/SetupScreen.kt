package com.tuxungo.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(
    onStart: (GameConfig) -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: (Int, Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeModelName = remember {
        val models = com.tuxungo.android.engine.ModelStore.list(context)
        val activeId = com.tuxungo.android.engine.ModelStore.activeId(context)
        models.firstOrNull { it.id == activeId }?.displayName ?: "内置默认模型"
    }
    var mode by remember { mutableStateOf(GameMode.PVP) }
    var width by remember { mutableIntStateOf(19) }
    var height by remember { mutableIntStateOf(19) }
    var handicap by remember { mutableIntStateOf(0) }
    var humanStone by remember { mutableStateOf(com.tuxungo.android.go.Stone.BLACK) }
    var aiVisits by remember { mutableIntStateOf(200) }
    var showUnsupportedDialog by remember { mutableStateOf(false) }

    val handicapSupported = width == height && width in listOf(9, 13, 19)
    val effectiveHandicap = if (handicapSupported) handicap else 0
    /** KataGo reliably supports boards with both sides in 7..19. */
    val aiSupported = (width in 7..19 && height in 7..19)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(text = "TuxunGo", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "免费开源的围棋对弈与 AI 分析应用，内置 KataGo 引擎。", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "对弈模式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == GameMode.PVP, onClick = { mode = GameMode.PVP }, label = { Text("人对人") })
            FilterChip(selected = mode == GameMode.PVE, onClick = { mode = GameMode.PVE }, label = { Text("人对 AI") })
            FilterChip(selected = mode == GameMode.EVE, onClick = { mode = GameMode.EVE }, label = { Text("AI 对 AI") })
        }
        if (mode == GameMode.EVE) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "两个 AI 使用当前模型轮流落子，可随时暂停，暂停时可分析局面。",
                fontSize = 13.sp, color = Color.Gray
            )
        }
        if (mode == GameMode.PVE) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "你执子", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = humanStone == com.tuxungo.android.go.Stone.BLACK, onClick = { humanStone = com.tuxungo.android.go.Stone.BLACK }, label = { Text("执黑") })
                FilterChip(selected = humanStone == com.tuxungo.android.go.Stone.WHITE, onClick = { humanStone = com.tuxungo.android.go.Stone.WHITE }, label = { Text("执白") })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "AI 难度", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = aiVisits == 50, onClick = { aiVisits = 50 }, label = { Text("简单") })
                FilterChip(selected = aiVisits == 200, onClick = { aiVisits = 200 }, label = { Text("中等") })
                FilterChip(selected = aiVisits == 800, onClick = { aiVisits = 800 }, label = { Text("困难") })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "棋盘大小", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = width == 19 && height == 19, onClick = { width = 19; height = 19 }, label = { Text("19×19") })
            FilterChip(selected = width == 13 && height == 13, onClick = { width = 13; height = 13 }, label = { Text("13×13") })
            FilterChip(selected = width == 9 && height == 9, onClick = { width = 9; height = 9 }, label = { Text("9×9") })
            FilterChip(selected = width == 13 && height == 9, onClick = { width = 13; height = 9 }, label = { Text("13×9") })
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "宽：$width 路", fontSize = 14.sp)
        Slider(
            value = width.toFloat(),
            onValueChange = { width = it.toInt() },
            valueRange = 5f..25f,
            steps = 19,
            modifier = Modifier.fillMaxWidth()
        )
        Text(text = "高：$height 路", fontSize = 14.sp)
        Slider(
            value = height.toFloat(),
            onValueChange = { height = it.toInt() },
            valueRange = 5f..25f,
            steps = 19,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (aiSupported) "KataGo 官方支持范围内" else "超出 KataGo 支持范围（7–19 路）：AI 下不了，人对人可以下",
            fontSize = 13.sp, color = if (aiSupported) Color.Gray else Color(0xFFC62828)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "让子（仅 9 / 13 / 19 路方棋盘）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in listOf(0, 2, 3, 4, 5)) {
                FilterChip(
                    selected = effectiveHandicap == n,
                    enabled = handicapSupported,
                    onClick = { handicap = n },
                    label = { Text(if (n == 0) "无" else "$n 子") }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in listOf(6, 7, 8, 9)) {
                FilterChip(
                    selected = effectiveHandicap == n,
                    enabled = handicapSupported,
                    onClick = { handicap = n },
                    label = { Text("$n 子") }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "贴目：7.5 目（中国规则）", fontSize = 13.sp, color = Color.Gray)
        }
        }
        Spacer(modifier = Modifier.height(20.dp))

        val needsAi = mode != GameMode.PVP
        val blocked = needsAi && !aiSupported
        Button(
            onClick = {
                if (blocked) {
                    showUnsupportedDialog = true
                } else {
                    onStart(
                        GameConfig(
                            mode, width, height, effectiveHandicap,
                            komi = 7.5, humanStone = humanStone, aiVisits = aiVisits
                        )
                    )
                }
            },
            colors = if (blocked) {
                androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White
                )
            } else {
                androidx.compose.material3.ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (blocked) "开始对局（AI 不支持此棋盘）" else "开始对局", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = { onOpenEditor(width, height) },
                modifier = Modifier.weight(1f)
            ) { Text("摆棋（自定义布局）") }
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            ) { Text("设置") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "AI 模型", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "当前模型：$activeModelName", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onOpenModels,
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开模型库（可上传自己的 KataGo 模型）") }
        }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "版本 0.1.0（开发中）", fontSize = 12.sp, color = Color.Gray)
    }

    if (showUnsupportedDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUnsupportedDialog = false },
            title = { Text("这个棋盘 AI 下不了") },
            text = {
                Text(
                    "当前棋盘是 ${width}×${height} 路，超出了 KataGo 引擎可靠支持的范围（宽和高都要在 7–19 路之间）。" +
                        "人对人模式不受影响，可以正常下。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUnsupportedDialog = false
                    mode = GameMode.PVP
                }) { Text("改下人对人") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsupportedDialog = false }) { Text("去改棋盘") }
            }
        )
    }
}
