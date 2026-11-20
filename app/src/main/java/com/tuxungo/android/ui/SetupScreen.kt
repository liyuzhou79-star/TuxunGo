package com.tuxungo.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(
    onStart: (GameConfig) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: (Int, Int) -> Unit,
    onOpenGame: (com.tuxungo.android.LibraryGame) -> Unit = {},
    onOpenTraining: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenGuide: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activeModelName = remember {
        val models = com.tuxungo.android.engine.ModelStore.list(context)
        val activeId = com.tuxungo.android.engine.ModelStore.activeId(context)
        models.firstOrNull { it.id == activeId }?.displayName ?: "轻量模型"
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
    var aiRank by remember {
        mutableStateOf(setupPrefs.getString("aiRank", "max") ?: "max")
    }
    var rankMenuOpen by remember { mutableStateOf(false) }
    // Mascot easter egg: five quick taps open the lifetime stats.
    var mascotTaps by remember { mutableIntStateOf(0) }
    var lastMascotTap by remember { mutableStateOf(0L) }
    var showEgg by remember { mutableStateOf(false) }
    var clockMinutes by remember { mutableIntStateOf(setupPrefs.getInt("clockMinutes", 0)) }
    var seriesTarget by remember { mutableIntStateOf(setupPrefs.getInt("seriesTarget", 0)) }
    var focusMode by remember { mutableStateOf(setupPrefs.getBoolean("focusMode", false)) }
    var oneColor by remember { mutableStateOf(setupPrefs.getBoolean("oneColor", false)) }
    var byoyomiPeriods by remember {
        mutableIntStateOf(setupPrefs.getInt("byoyomiPeriods", 3))
    }
    var showUnsupportedDialog by remember { mutableStateOf(false) }
    // The home page is deliberately minimal now: quick start, the
    // start button and entry cards. Everything adjustable lives on
    // the detail page behind the settings card.
    var showDetail by remember { mutableStateOf(false) }
    var showNigiri by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(
        mode, width, height, handicapText, komiText, humanStone, aiVisits,
        clockMinutes, byoyomiPeriods, seriesTarget, focusMode, oneColor, aiRank
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
            .putBoolean("oneColor", oneColor)
            .putString("aiRank", aiRank)
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
    val needsAi = mode != GameMode.PVP
    val blocked = needsAi && !aiSupported
    fun startGame() {
        if (blocked) {
            showUnsupportedDialog = true
        } else {
            if (mode == GameMode.EVE && seriesTarget > 0) {
                com.tuxungo.android.SeriesStore.reset(context)
            }
            if (mode == GameMode.PVE) {
                // The chosen rank cap applies to the AI's side.
                val aiStone = humanStone.other()
                val cur = com.tuxungo.android.engine.SideAiStore.load(
                    context, aiStone, aiVisits
                )
                com.tuxungo.android.engine.SideAiStore.save(
                    context, aiStone, cur.copy(rank = aiRank, visits = aiVisits)
                )
            }
            onStart(
                GameConfig(
                    mode, width, height, effectiveHandicap,
                    komi = komiInput, humanStone = humanStone, aiVisits = aiVisits,
                    clockMinutes = clockMinutes,
                    byoyomiPeriods = byoyomiPeriods,
                    seriesTarget = if (mode == GameMode.EVE) seriesTarget else 0,
                    focusMode = focusMode,
                    oneColor = if (mode != GameMode.EVE) oneColor else false
                )
            )
        }
    }
    val modeLabel = when (mode) {
        GameMode.PVP -> "人对人"
        GameMode.PVE -> "人对 AI"
        GameMode.EVE -> "AI 对 AI"
    }
    val configSummary = modeLabel + " · ${width}×${height} · " +
        if (clockMinutes > 0) "${clockMinutes} 分钟" else "不限时"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            // The mascot. Five quick taps in a row open the
            // hidden lifetime-stats page (an easter egg).
            Mascot(
                modifier = Modifier
                    .size(56.dp)
                    .clickable {
                        val now = System.currentTimeMillis()
                        mascotTaps = if (now - lastMascotTap > 1500) {
                            1
                        } else {
                            mascotTaps + 1
                        }
                        lastMascotTap = now
                        if (mascotTaps >= 5) {
                            mascotTaps = 0
                            showEgg = true
                        }
                    }
            )
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

        if (showDetail) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { showDetail = false }) { Text("‹ 返回", fontSize = 15.sp) }
            Text(text = "对局设置", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
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
        if (mode != GameMode.EVE) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("一色棋", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "双方棋子都显示黑色，全靠记忆下棋；顶栏点「显色」可以偷看一眼",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
            Switch(checked = oneColor, onCheckedChange = { oneColor = it })
        }
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
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "AI 棋力定级", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { rankMenuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("棋力：${com.tuxungo.android.engine.SideAiStore.rankLabel(aiRank)}") }
                androidx.compose.material3.DropdownMenu(
                    expanded = rankMenuOpen,
                    onDismissRequest = { rankMenuOpen = false }
                ) {
                    for (r in com.tuxungo.android.engine.SideAiStore.RANKS) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(com.tuxungo.android.engine.SideAiStore.rankLabel(r))
                            },
                            onClick = {
                                rankMenuOpen = false
                                aiRank = r
                            }
                        )
                    }
                }
            }
            Text(
                text = "定级后，AI 按这个级别的真人棋风下棋，会犯这个级别常犯的错，不是故意乱下；Max 为全部实力。局内的「AI 托管」面板里也能随时改，AI 对 AI 时双方可以分别定级。",
                fontSize = 12.sp, color = Color.Gray
            )
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
        if (mode != GameMode.EVE) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "猜先", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "按围棋的老规矩决定谁执黑：一方抓一把白子，另一方猜单双，猜中者执黑。",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showNigiri = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("开始猜先") }
                }
            }
        }
        }
        Spacer(modifier = Modifier.height(20.dp))

        val startInteraction = remember {
            androidx.compose.foundation.interaction.MutableInteractionSource()
        }
        val startPressed by startInteraction.collectIsPressedAsState()
        val startScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (startPressed) 0.98f else 1f,
            label = "startButtonPress"
        )
        Button(
            onClick = { startGame() },
            colors = if (blocked) {
                androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White
                )
            } else {
                androidx.compose.material3.ButtonDefaults.buttonColors()
            },
            interactionSource = startInteraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .graphicsLayer { scaleX = startScale; scaleY = startScale }
        ) {
            Text(
                text = if (blocked) "开始对局（AI 不支持此棋盘）" else "开始对局",
                fontSize = 17.sp
            )
        }
        if (showDetail) {
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = { onOpenEditor(width, height) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("摆棋（自己摆局面开局）") }
        }
        if (!showDetail) {
        Spacer(modifier = Modifier.height(20.dp))
        MenuCard(
            title = "对局设置",
            desc = "模式 · 棋盘 · 让子贴目 · 棋钟 · 猜先",
            foot = "当前：$configSummary",
            icon = AppIconKind.SETTINGS,
            onClick = { showDetail = true }
        )
        Spacer(modifier = Modifier.height(12.dp))
        MenuCard(
            title = "训练",
            desc = "猜一选 · 死活题，慢慢涨棋",
            foot = "今日一题和错题本都在这里",
            icon = AppIconKind.TRAINING,
            onClick = onOpenTraining
        )
        Spacer(modifier = Modifier.height(12.dp))
        MenuCard(
            title = "资料与工具",
            desc = "棋谱库 · 我的数据 · AI 模型 · 实体棋盘棋钟",
            foot = "当前模型：$activeModelName",
            icon = AppIconKind.LIBRARY,
            onClick = onOpenTools
        )
        if (recentGames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "最近棋谱", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            for (g in recentGames) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onOpenGame(g) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    GameThumb(g, modifier = Modifier.size(46.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${g.title} · ${g.moves.size} 手",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            java.text.SimpleDateFormat(
                                "MM-dd HH:mm",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(g.createdAt)) +
                                if (g.resultText.isNotBlank()) {
                                    " · ${g.resultText}"
                                } else {
                                    ""
                                },
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
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
            TextButton(onClick = onOpenSettings) { Text("设置", fontSize = 12.sp) }
            TextButton(onClick = onOpenGuide) { Text("新手指南", fontSize = 12.sp) }
        }
    }

    if (showNigiri) {
        NigiriDialog(
            mode = mode,
            onDismiss = { showNigiri = false },
            onPveResult = { stone -> humanStone = stone }
        )
    }

    if (showEgg) {
        EggStatsDialog(onDismiss = { showEgg = false })
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
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        label = "modeOptionPress"
    )
    Card(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        onClick = onClick,
        interactionSource = interaction,
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
        Box {
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
            if (selected) {
                // Check badge on the selected card's corner.
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .size(18.dp)
                        .background(colors.primary, CircleShape),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(text = "✓", fontSize = 11.sp, color = colors.onPrimary)
                }
            }
        }
    }
}


/**
 * Nigiri （猜先）: one side grabs a handful of white stones, the other
 * guesses odd or even; a correct guess takes Black. Against the AI
 * the app grabs and the user guesses, and the result is applied to
 * the "you play" colour. Between two humans it is a pass-the-phone
 * ceremony and only announces who takes Black.
 */
@Composable
private fun NigiriDialog(
    mode: GameMode,
    onDismiss: () -> Unit,
    onPveResult: (com.tuxungo.android.go.Stone) -> Unit
) {
    val count = remember { kotlin.random.Random.nextInt(1, 11) }
    var phase by remember { mutableIntStateOf(0) }
    var guessedOdd by remember { mutableStateOf<Boolean?>(null) }
    val isOdd = count % 2 == 1
    val guesserWon = guessedOdd != null && guessedOdd == isOdd
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("猜先") },
        text = {
            Column {
                when {
                    phase == 0 && mode == GameMode.PVE -> {
                        Text(
                            "AI 已经抓了一把白子捂在手里。猜猜是单数还是双数：猜中了你执黑，猜错了你执白。",
                            fontSize = 14.sp
                        )
                    }
                    phase == 0 -> {
                        Text(
                            "玩家一先抓一把白子（自己心里有数就行），然后把手机递给玩家二来猜。",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { phase = 1 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("抓好了，递给玩家二") }
                    }
                    phase == 1 -> {
                        Text(
                            "玩家二猜一猜：玩家一抓的子是单数还是双数？猜中了玩家二执黑，猜错了玩家一执黑。",
                            fontSize = 14.sp
                        )
                    }
                    else -> {
                        // Reveal: draw the grabbed stones.
                        val rows = (1..count).chunked(5)
                        for (row in rows) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (i in row) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Color.White, CircleShape)
                                            .border(1.dp, Color(0xFF9A9A9A), CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            text = "一共 $count 子，是${if (isOdd) "单" else "双"}数。",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (mode == GameMode.PVE) {
                                if (guesserWon) "猜中了！你执黑。" else "猜错了，你执白。"
                            } else {
                                if (guesserWon) "玩家二猜中了，玩家二执黑。" else "玩家二猜错了，玩家一执黑。"
                            },
                            fontSize = 14.sp, color = Color(0xFF2F7CF6)
                        )
                    }
                }
                if (phase == 1 || (phase == 0 && mode == GameMode.PVE)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                guessedOdd = true
                                phase = 2
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("猜单数") }
                        Button(
                            onClick = {
                                guessedOdd = false
                                phase = 2
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("猜双数") }
                    }
                }
            }
        },
        confirmButton = {
            if (phase == 2) {
                Button(onClick = {
                    if (mode == GameMode.PVE) {
                        onPveResult(
                            if (guesserWon) {
                                com.tuxungo.android.go.Stone.BLACK
                            } else {
                                com.tuxungo.android.go.Stone.WHITE
                            }
                        )
                    }
                    onDismiss()
                }) { Text(if (mode == GameMode.PVE) "就这样，回去开局" else "知道了") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** One label/value row of the easter-egg stats page. */
@Composable
private fun EggRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = label, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EggSection(title: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD64F7C))
    Spacer(modifier = Modifier.height(2.dp))
}

/**
 * The mascot easter egg: five quick taps on the home mascot open
 * this lifetime record — every stone played, every game opened,
 * black/white win rates and the training numbers, grouped in
 * sections. Data comes from the game library (finished games are
 * auto-saved there) and the training stores.
 */
@Composable
private fun EggStatsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val games = remember { com.tuxungo.android.LibraryStore.listGames(context) }
    val totalMoves = games.sumOf { it.moves.size }
    val blackWins = games.count { it.resultText.startsWith("黑") }
    val whiteWins = games.count { it.resultText.startsWith("白") }
    val decided = blackWins + whiteWins
    val unfinished = games.size - decided
    val longest = games.maxByOrNull { it.moves.size }
    val avgMoves = if (games.isNotEmpty()) totalMoves / games.size else 0
    fun pct(n: Int): String =
        if (decided > 0) String.format("%.1f%%", n * 100.0 / decided) else "—"
    val tsumegoPrefs = remember {
        context.getSharedPreferences("tsumego_prefs", android.content.Context.MODE_PRIVATE)
    }
    val solvedCount = (tsumegoPrefs.getString("solved", "") ?: "")
        .split(",").count { it.isNotBlank() }
    var tsumegoMiss = 0
    for (i in TSUMEGO_PROBLEMS.indices) {
        tsumegoMiss += tsumegoPrefs.getInt("miss_$i", 0)
    }
    val guessPrefs = remember {
        context.getSharedPreferences("guess_prefs", android.content.Context.MODE_PRIVATE)
    }
    val guessBest = guessPrefs.getInt("bestScore", 0)
    val guessStreak = guessPrefs.getInt("bestStreak", 0)
    val guessMistakes = (guessPrefs.getString("mistakes", "") ?: "")
        .split("|").count { it.isNotBlank() }
    val byMode = games.groupBy { it.modeLabel }
        .entries.sortedByDescending { it.value.size }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Mascot(modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("彩蛋 · 你的围棋档案", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "连点吉祥物五次找到的隐藏页，记录你在这个 App 里的全部战绩。",
                    fontSize = 12.sp, color = Color.Gray
                )
                EggSection("对局")
                EggRow("累计对局", "${games.size} 局")
                EggRow("累计落子", "$totalMoves 子")
                EggRow("平均每局", "$avgMoves 手")
                EggRow(
                    "最长一局",
                    longest?.let { "${it.moves.size} 手（${it.title}）" } ?: "—"
                )
                EggSection("胜负（已分胜负 $decided 局）")
                EggRow("黑方胜", "$blackWins 局 · ${pct(blackWins)}")
                EggRow("白方胜", "$whiteWins 局 · ${pct(whiteWins)}")
                EggRow("未分胜负", "$unfinished 局")
                if (byMode.isNotEmpty()) {
                    EggSection("各模式")
                    for ((label, list) in byMode) {
                        EggRow(label, "${list.size} 局 · ${list.sumOf { it.moves.size }} 子")
                    }
                }
                EggSection("训练")
                EggRow("死活题已解", "$solvedCount / ${TSUMEGO_PROBLEMS.size} 题")
                EggRow("死活题累计错次", "$tsumegoMiss 次")
                EggRow("猜一选最高分", "$guessBest 分")
                EggRow("猜一选最高连对", "$guessStreak 题")
                EggRow("猜一选错题待复习", "$guessMistakes 题")
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "数据来自棋谱库（已结束的对局会自动保存）和训练记录。",
                    fontSize = 11.sp, color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
