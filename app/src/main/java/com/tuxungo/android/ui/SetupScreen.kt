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
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: (Int, Int) -> Unit,
    onOpenGuess: () -> Unit = {},
    onOpenGame: (com.tuxungo.android.LibraryGame) -> Unit = {},
    onOpenTsumego: () -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeModelName = remember {
        val models = com.tuxungo.android.engine.ModelStore.list(context)
        val activeId = com.tuxungo.android.engine.ModelStore.activeId(context)
        models.firstOrNull { it.id == activeId }?.displayName ?: "内置默认模型"
    }
    val recentGames = remember {
        com.tuxungo.android.LibraryStore.listGames(context).take(3)
    }
    // The setup form remembers the last used configuration.
    val setupPrefs = remember {
        context.getSharedPreferences("setup_prefs", android.content.Context.MODE_PRIVATE)
    }
    var mode by remember {
        mutableStateOf(
            runCatching {
                GameMode.valueOf(setupPrefs.getString("mode", "PVP") ?: "PVP")
            }.getOrDefault(GameMode.PVP)
        )
    }
    var width by remember { mutableIntStateOf(setupPrefs.getInt("width", 19)) }
    var height by remember { mutableIntStateOf(setupPrefs.getInt("height", 19)) }
    var handicapText by remember {
        mutableStateOf(setupPrefs.getString("handicapText", "0") ?: "0")
    }
    var komiText by remember {
        mutableStateOf(setupPrefs.getString("komiText", "7.5") ?: "7.5")
    }
    var humanStone by remember {
        mutableStateOf(
            runCatching {
                com.tuxungo.android.go.Stone.valueOf(
                    setupPrefs.getString("humanStone", "BLACK") ?: "BLACK"
                )
            }.getOrDefault(com.tuxungo.android.go.Stone.BLACK)
        )
    }
    var aiVisits by remember { mutableIntStateOf(setupPrefs.getInt("aiVisits", 200)) }
    var clockMinutes by remember { mutableIntStateOf(setupPrefs.getInt("clockMinutes", 0)) }
    var byoyomiPeriods by remember {
        mutableIntStateOf(setupPrefs.getInt("byoyomiPeriods", 3))
    }
    var showUnsupportedDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(
        mode, width, height, handicapText, komiText, humanStone, aiVisits,
        clockMinutes, byoyomiPeriods
    ) {
        setupPrefs.edit()
            .putString("mode", mode.name)
            .putInt("width", width)
            .putInt("height", height)
            .putString("handicapText", handicapText)
            .putString("komiText", komiText)
            .putString("humanStone", humanStone.name)
            .putInt("aiVisits", aiVisits)
            .putInt("clockMinutes", clockMinutes)
            .putInt("byoyomiPeriods", byoyomiPeriods)
            .apply()
    }

    val handicapSupported = width == height && width in listOf(9, 13, 19)
    val handicapInput = handicapText.toIntOrNull()?.coerceIn(0, 9) ?: 0
    // A 1-stone "handicap" is just an even game with adjusted komi.
    val effectiveHandicap = if (handicapSupported && handicapInput != 1) handicapInput else 0
    // Handicap games are normally played with a half-point komi: when
    // a handicap is picked and the komi still shows the even-game
    // default, switch it (and back when the handicap is removed).
    // A komi the user typed themselves is never touched.
    androidx.compose.runtime.LaunchedEffect(effectiveHandicap) {
        if (effectiveHandicap >= 2 && komiText == "7.5") {
            komiText = "0.5"
        } else if (effectiveHandicap == 0 && komiText == "0.5") {
            komiText = "7.5"
        }
    }
    val komiInput = komiText.toDoubleOrNull()?.coerceIn(0.0, 20.0) ?: 7.5
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
        Text(text = "快速开始", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    onStart(GameConfig(mode = GameMode.PVE, width = 9, height = 9, aiVisits = 200))
                },
                modifier = Modifier.weight(1f)
            ) { Text("9 路快棋") }
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    onStart(GameConfig(mode = GameMode.PVE, width = 19, height = 19, aiVisits = 200))
                },
                modifier = Modifier.weight(1f)
            ) { Text("19 路标准局") }
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    onStart(GameConfig(mode = GameMode.EVE, width = 19, height = 19, aiVisits = 200))
                },
                modifier = Modifier.weight(1f)
            ) { Text("AI 表演赛") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "一键开局，参数按常用配置设好；想细调可以用下面的设置。", fontSize = 12.sp, color = Color.Gray)
        }
        }
        Spacer(modifier = Modifier.height(16.dp))

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

        Text(text = "用时（棋钟）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = clockMinutes == 0, onClick = { clockMinutes = 0 }, label = { Text("不限时") })
            FilterChip(selected = clockMinutes == 10, onClick = { clockMinutes = 10 }, label = { Text("10 分钟") })
            FilterChip(selected = clockMinutes == 20, onClick = { clockMinutes = 20 }, label = { Text("20 分钟") })
            FilterChip(selected = clockMinutes == 30, onClick = { clockMinutes = 30 }, label = { Text("30 分钟") })
        }
        if (clockMinutes > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(text = "读秒次数", fontSize = 13.sp, color = Color.Gray)
                for (n in listOf(3, 5)) {
                    FilterChip(
                        selected = byoyomiPeriods == n,
                        onClick = { byoyomiPeriods = n },
                        label = { Text("$n 次") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "每方主时间 ${clockMinutes} 分钟，用完进入读秒（${byoyomiPeriods} 次 × 30 秒），读秒也用完判负。第一手落子后开始计时。",
                fontSize = 12.sp, color = Color.Gray
            )
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

        Text(text = "让子与贴目（自己填）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = handicapText,
                onValueChange = { v -> handicapText = v.filter { it.isDigit() }.take(1) },
                label = { Text("让子数") },
                enabled = handicapSupported,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.OutlinedTextField(
                value = komiText,
                onValueChange = { v -> komiText = v.filter { it.isDigit() || it == '.' }.take(5) },
                label = { Text("贴目") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                ),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (handicapSupported) {
                "让子填 2–9（仅 9 / 13 / 19 路方棋盘）；填 0 或 1 按分先处理。贴目随便填：中国规则常用 7.5，日本规则常用 6.5，不贴目填 0。"
            } else {
                "当前棋盘不支持让子（仅 9 / 13 / 19 路方棋盘可以）。贴目随便填：中国规则常用 7.5，日本规则常用 6.5，不贴目填 0。"
            },
            fontSize = 13.sp, color = Color.Gray
        )
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
                            komi = komiInput, humanStone = humanStone, aiVisits = aiVisits,
                            clockMinutes = clockMinutes,
                            byoyomiPeriods = byoyomiPeriods
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
            ) { Text("摆棋") }
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
        Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "猜一选训练", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        val guessBestText = remember {
            val p = context.getSharedPreferences(
                "guess_prefs", android.content.Context.MODE_PRIVATE
            )
            val bs = p.getInt("bestScore", 0)
            val bk = p.getInt("bestStreak", 0)
            if (bs > 0 || bk > 0) "历史最佳：得分 $bs · 连对 $bk。" else ""
        }
        Text(
            text = "AI 自对弈逐手推进，你来猜每一步的一选，练棋感和大局观。" + guessBestText,
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onOpenGuess,
            modifier = Modifier.fillMaxWidth()
        ) { Text("开始猜一选") }
        }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "死活题训练", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        val tsumegoSolvedCount = remember {
            val s = context.getSharedPreferences(
                "tsumego_prefs", android.content.Context.MODE_PRIVATE
            ).getString("solved", "") ?: ""
            if (s.isBlank()) {
                0
            } else {
                s.split(",").count { it.isNotBlank() }
            }
        }
        Text(
            text = "从紧气、双吃到对杀、征子，做对一步，对方自动应招，练读秒般的计算力。" +
                "已解 $tsumegoSolvedCount / ${TSUMEGO_PROBLEMS.size} 题。",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onOpenTsumego,
            modifier = Modifier.fillMaxWidth()
        ) { Text("开始做死活题") }
        }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "棋谱库", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "收藏的对局都在这里，点开就能复盘整场。", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onOpenLibrary,
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开棋谱库") }
        }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "我的数据", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "对局总览、黑白胜负分布、最近对局手数和训练进度，都在本机统计。", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onOpenStats,
            modifier = Modifier.fillMaxWidth()
        ) { Text("查看我的数据") }
        }
        }
        if (recentGames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "最近棋谱", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            for (g in recentGames) {
                androidx.compose.material3.TextButton(
                    onClick = { onOpenGame(g) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${g.title} · ${g.moves.size} 手",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "版本 1.15.0", fontSize = 12.sp, color = Color.Gray)
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
