package com.tuxungo.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
    onOpenTsumego: (Int) -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenPhysicalClock: () -> Unit = {}
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
    var seriesTarget by remember { mutableIntStateOf(setupPrefs.getInt("seriesTarget", 0)) }
    var focusMode by remember { mutableStateOf(setupPrefs.getBoolean("focusMode", false)) }
    var byoyomiPeriods by remember {
        mutableIntStateOf(setupPrefs.getInt("byoyomiPeriods", 3))
    }
    var showUnsupportedDialog by remember { mutableStateOf(false) }
    // First-launch guide: three pages, shown once, re-openable
    // from the footer.
    var showGuide by remember {
        mutableStateOf(!setupPrefs.getBoolean("guide_seen", false))
    }
    var guidePage by remember { mutableIntStateOf(0) }
    fun closeGuide() {
        setupPrefs.edit().putBoolean("guide_seen", true).apply()
        showGuide = false
    }

    androidx.compose.runtime.LaunchedEffect(
        mode, width, height, handicapText, komiText, humanStone, aiVisits,
        clockMinutes, byoyomiPeriods, seriesTarget, focusMode
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
            .putInt("seriesTarget", seriesTarget)
            .putBoolean("focusMode", focusMode)
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // Logo block: theme-colored tile with a black and a
            // white stone, echoing the app icon.
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(15.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .offset(x = 8.dp, y = (-8).dp)
                        .background(Color(0xFF1A1A1A), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .background(Color.White, CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = "TuxunGo", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "免费开源的围棋对弈与 AI 分析",
                    fontSize = 13.sp, color = Color.Gray
                )
                Text(
                    text = "内置 KataGo 引擎 · 完全离线",
                    fontSize = 13.sp, color = Color.Gray
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
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
            Button(
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "对弈模式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeOption(
                title = "人对人",
                desc = "和朋友轮流落子",
                selected = mode == GameMode.PVP,
                modifier = Modifier.weight(1f)
            ) { mode = GameMode.PVP }
            ModeOption(
                title = "人对 AI",
                desc = "挑战 KataGo",
                selected = mode == GameMode.PVE,
                modifier = Modifier.weight(1f)
            ) { mode = GameMode.PVE }
            ModeOption(
                title = "AI 对 AI",
                desc = "看 AI 互搏",
                selected = mode == GameMode.EVE,
                modifier = Modifier.weight(1f)
            ) { mode = GameMode.EVE }
        }
        if (mode == GameMode.EVE) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "两个 AI 使用当前模型轮流落子，可随时暂停，暂停时可分析局面。",
                fontSize = 13.sp, color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("专注模式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "隐藏胜率、一选提示和形势分析，专心下棋；后台照常算，终局复盘不受影响",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
            Switch(checked = focusMode, onCheckedChange = { focusMode = it })
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

        if (mode == GameMode.EVE) {
            Text(text = "系列赛（表演赛）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = seriesTarget == 0, onClick = { seriesTarget = 0 }, label = { Text("不开启") })
                FilterChip(selected = seriesTarget == 2, onClick = { seriesTarget = 2 }, label = { Text("三局两胜") })
                FilterChip(selected = seriesTarget == 3, onClick = { seriesTarget = 3 }, label = { Text("五局三胜") })
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "开启后两个 AI 交换黑白连打多局，先赢满局数的一方夺冠，每局结束自动开下一局。双方用哪个模型、各自多强，在对局里的 AI 托管面板分别设置。",
                fontSize = 12.sp, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(text = "用时（棋钟）", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = clockMinutes == 0, onClick = { clockMinutes = 0 }, label = { Text("不限时") })
            FilterChip(selected = clockMinutes == 5, onClick = { clockMinutes = 5 }, label = { Text("5 分钟") })
            FilterChip(selected = clockMinutes == 10, onClick = { clockMinutes = 10 }, label = { Text("10 分钟") })
            FilterChip(selected = clockMinutes == 20, onClick = { clockMinutes = 20 }, label = { Text("20 分钟") })
            FilterChip(selected = clockMinutes == 30, onClick = { clockMinutes = 30 }, label = { Text("30 分钟") })
            FilterChip(selected = clockMinutes == 60, onClick = { clockMinutes = 60 }, label = { Text("60 分钟") })
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
                    if (mode == GameMode.EVE && seriesTarget > 0) {
                        com.tuxungo.android.SeriesStore.reset(context)
                    }
                    onStart(
                        GameConfig(
                            mode, width, height, effectiveHandicap,
                            komi = komiInput, humanStone = humanStone, aiVisits = aiVisits,
                            clockMinutes = clockMinutes,
                            byoyomiPeriods = byoyomiPeriods,
                            seriesTarget = if (mode == GameMode.EVE) seriesTarget else 0,
                            focusMode = focusMode
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
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(text = if (blocked) "开始对局（AI 不支持此棋盘）" else "开始对局", fontSize = 17.sp)
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
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("训练")
        Spacer(modifier = Modifier.height(10.dp))
        val guessBestFoot = remember {
            val p = context.getSharedPreferences(
                "guess_prefs", android.content.Context.MODE_PRIVATE
            )
            val bs = p.getInt("bestScore", 0)
            val bk = p.getInt("bestStreak", 0)
            if (bs > 0 || bk > 0) "最佳 $bs 分 · 连对 $bk" else "暂无成绩"
        }
        val tsumegoSolvedSet = remember {
            val s = context.getSharedPreferences(
                "tsumego_prefs", android.content.Context.MODE_PRIVATE
            ).getString("solved", "") ?: ""
            if (s.isBlank()) {
                emptySet()
            } else {
                s.split(",").mapNotNull { it.toIntOrNull() }.toSet()
            }
        }
        val tsumegoSolvedCount = tsumegoSolvedSet.size
        // Daily problem: rotate through the unsolved ones by day of
        // year, so the card always recommends a fresh problem.
        val dailyTsumego = remember {
            val unsolved = TSUMEGO_PROBLEMS.indices.filter {
                it !in tsumegoSolvedSet
            }
            if (unsolved.isEmpty()) {
                -1
            } else {
                val day = java.util.Calendar.getInstance()
                    .get(java.util.Calendar.DAY_OF_YEAR)
                unsolved[day % unsolved.size]
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavCard(
                title = "猜一选",
                desc = "猜 AI 每一步的一选，练棋感和大局观",
                foot = guessBestFoot,
                modifier = Modifier.weight(1f),
                onClick = onOpenGuess
            )
            NavCard(
                title = "死活题",
                desc = "紧气、双吃、对杀、征子 · 已解 $tsumegoSolvedCount/${TSUMEGO_PROBLEMS.size}",
                foot = if (dailyTsumego >= 0) {
                    "今日一题：${TSUMEGO_PROBLEMS[dailyTsumego].title}"
                } else {
                    "全部解完，太强了"
                },
                modifier = Modifier.weight(1f),
                onClick = {
                    onOpenTsumego(if (dailyTsumego >= 0) dailyTsumego else 0)
                }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("资料与工具")
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavCard(
                title = "棋谱库",
                desc = "收藏的对局都在这里，点开就能复盘",
                foot = "打开棋谱库",
                modifier = Modifier.weight(1f),
                onClick = onOpenLibrary
            )
            NavCard(
                title = "我的数据",
                desc = "对局总览、黑白胜负和训练进度",
                foot = "查看统计",
                modifier = Modifier.weight(1f),
                onClick = onOpenStats
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
        Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "实体棋盘棋钟", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "两个人用真棋盘下棋，手机当棋钟：下完一步点自己这边", fontSize = 13.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onOpenPhysicalClock,
            modifier = Modifier.fillMaxWidth()
        ) { Text("打开棋钟") }
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
                        "${g.title} · ${g.moves.size} 手 · " +
                            java.text.SimpleDateFormat(
                                "MM-dd HH:mm",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(g.createdAt)),
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = "版本 " + remember {
                    try {
                        context.packageManager
                            .getPackageInfo(context.packageName, 0)
                            .versionName ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                },
                fontSize = 12.sp, color = Color.Gray
            )
            TextButton(onClick = {
                guidePage = 0
                showGuide = true
            }) { Text("新手指南", fontSize = 12.sp) }
        }
    }

    if (showGuide) {
        val guideTitles = listOf("欢迎来到 TuxunGo", "AI 陪你看棋", "练棋与模型")
        val guideBodies = listOf(
            "内置 KataGo 引擎的免费围棋 App，完全离线、没有账号。选个模式就能开局：人对 AI、AI 对 AI、人对人；棋盘 5–25 路任意宽高，让子、贴目都能自由设。",
            "下棋时点「AI 分析」看候选着点和胜率，「形式判断」看地盘与死活。对局时每一手的胜率会在后台悄悄算好，终局打开复盘基本秒开：恶手、妙手、关键手自动标出，还能从任意一手试下分支。",
            "主页有猜一选和死活题两种训练：猜错的题会进错题本，死活题每天推荐一道。模型库可以导入更强的 KataGo 模型（.txt.gz / .bin.gz），每个模型还能测速。祝你棋力大涨！"
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { closeGuide() },
            title = { Text(guideTitles[guidePage]) },
            text = { Text(guideBodies[guidePage], fontSize = 14.sp) },
            confirmButton = {
                if (guidePage < 2) {
                    Button(onClick = { guidePage++ }) {
                        Text("下一页（${guidePage + 1}/3）")
                    }
                } else {
                    Button(onClick = { closeGuide() }) { Text("开始使用") }
                }
            },
            dismissButton = {
                if (guidePage < 2) {
                    TextButton(onClick = { closeGuide() }) { Text("跳过") }
                }
            }
        )
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

/** Section header with a small accent bar. */
@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 17.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Selectable mode card on the setup screen. */
@Composable
private fun ModeOption(
    title: String,
    desc: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                colors.primaryContainer
            } else {
                colors.surfaceVariant
            }
        ),
        border = if (selected) {
            BorderStroke(1.5.dp, colors.primary)
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    colors.onPrimaryContainer
                } else {
                    colors.onSurface
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

/** Navigation card used in the training / library grids. */
@Composable
private fun NavCard(
    title: String,
    desc: String,
    foot: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.height(118.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, fontSize = 12.sp, color = Color.Gray, maxLines = 2)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = foot,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary
            )
        }
    }
}
