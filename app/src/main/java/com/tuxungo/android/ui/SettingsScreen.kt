package com.tuxungo.android.ui

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.tuxungo.android.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungo.android.AppSettings
import com.tuxungo.android.SettingsStore

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccentChange: (String) -> Unit = {},
    onThemeModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }

    val previewPool = remember {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    DisposableEffect(Unit) { onDispose { previewPool.release() } }

    fun update(next: AppSettings) {
        settings = next
        SettingsStore.save(context, next)
    }

    // Settings are grouped into sub-pages so the screen is not one
    // long crowded list: "home" shows the groups, the rest show one
    // group's controls each.
    var page by remember { mutableStateOf("home") }
    val pageTitle = when (page) {
        "play" -> "对局与操作"
        "look" -> "外观"
        "sound" -> "声音与震动"
        "judge" -> "形势判断"
        "data" -> "数据与更新"
        else -> "设置"
    }

    fun previewFromList(resList: List<Int>) {
        val sid = previewPool.load(context, resList.random(), 1)
        // SoundPool loads asynchronously; retry briefly until ready.
        Thread {
            var tries = 0
            while (tries < 20) {
                if (previewPool.play(sid, 0.9f, 0.9f, 1, 0, 1f) != 0) break
                Thread.sleep(50)
                tries++
            }
        }.start()
    }

    fun previewPlace(id: String) = previewFromList(SettingsStore.placeSoundResList(id))

    fun previewCapture(id: String) = previewFromList(SettingsStore.captureSoundResList(id))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        if (page == "home") {
            ScreenHeader("设置", "按类别分好，一点都不挤", onBack)
            Spacer(modifier = Modifier.height(8.dp))
            MenuCard(
                title = "对局与操作",
                desc = "确认落子、坐标、手数、屏幕常亮、观战节奏、自动分析",
                foot = "",
                icon = AppIconKind.EDIT,
                onClick = { page = "play" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = "外观",
                desc = "主题色、棋盘样式（带实时预览）",
                foot = "",
                icon = AppIconKind.APPEARANCE,
                onClick = { page = "look" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = "声音与震动",
                desc = "落子与提子音效、震动反馈",
                foot = "",
                icon = AppIconKind.SOUND,
                onClick = { page = "sound" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = "形势判断",
                desc = "领地着色蔓延与浓淡、判断计算量、死子严格度、面板显示",
                foot = "",
                icon = AppIconKind.JUDGE,
                onClick = { page = "judge" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            MenuCard(
                title = "数据与更新",
                desc = "备份与恢复、检查更新、恢复默认设置",
                foot = "",
                icon = AppIconKind.LIBRARY,
                onClick = { page = "data" }
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { page = "home" }) {
                    Text("‹ 返回", fontSize = 15.sp)
                }
                Text(
                    text = pageTitle, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (page == "play") {
        Text(text = "落子方式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "确认落子", fontSize = 15.sp)
                Text(text = "点一次预览，再点一次才落子，防误触", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.confirmMove,
                onCheckedChange = { update(settings.copy(confirmMove = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "显示坐标", fontSize = 15.sp)
                Text(text = "棋盘四周的字母与数字", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.showCoordinates,
                onCheckedChange = { update(settings.copy(showCoordinates = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "棋盘铺满屏幕", fontSize = 15.sp)
                Text(text = "棋盘边贴到屏幕边（铺满时不显示坐标）", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.boardFill,
                onCheckedChange = { update(settings.copy(boardFill = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "落子放大镜", fontSize = 15.sp)
                Text(text = "待确认落子时，角上放大周围 5×5", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.magnifier,
                onCheckedChange = { update(settings.copy(magnifier = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "显示手数", fontSize = 15.sp)
                Text(text = "在棋子上标出第几手落的", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.showMoveNumbers,
                onCheckedChange = { update(settings.copy(showMoveNumbers = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "对局时屏幕常亮", fontSize = 15.sp)
                Text(text = "长考和观战 AI 对决时屏幕不熄灭", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.keepScreenOn,
                onCheckedChange = { update(settings.copy(keepScreenOn = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "智能裁判", fontSize = 15.sp)
                Text(text = "局面完全收官时自动判定胜负（先展示，3 秒后判终）", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.refereeEnabled,
                onCheckedChange = { update(settings.copy(refereeEnabled = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "终局自动存图", fontSize = 15.sp)
                Text(text = "对局结束时把终局局面图自动存到相册", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.autoSaveImage,
                onCheckedChange = { update(settings.copy(autoSaveImage = it)) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "AI 对 AI 观战节奏", fontSize = 15.sp)
        Text(text = "两个 AI 对弈时每手之间的停顿", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in SettingsStore.EVE_SPEEDS) {
                FilterChip(
                    selected = settings.eveSpeed == id,
                    onClick = { update(settings.copy(eveSpeed = id)) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "对局自动分析", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "对局时后台默默算每一手的胜率（终局复盘秒开靠它）。深度更准但更耗电。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((v, label) in SettingsStore.AUTO_VISITS) {
                FilterChip(
                    selected = settings.autoVisits == v,
                    onClick = { update(settings.copy(autoVisits = v)) },
                    label = { Text(label) }
                )
            }
        }
        }

        if (page == "look") {
        Text(text = "外观模式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in SettingsStore.THEME_MODES) {
                FilterChip(
                    selected = settings.themeMode == id,
                    onClick = {
                        update(settings.copy(themeMode = id))
                        onThemeModeChange(id)
                    },
                    label = { Text(label) }
                )
            }
        }
        Text(
            text = "深色模式只改变界面配色，棋盘和棋子保持原样；晚上看棋不刺眼。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "主题色", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, label) in SettingsStore.ACCENTS) {
                FilterChip(
                    selected = settings.accent == id,
                    onClick = {
                        update(settings.copy(accent = id))
                        onAccentChange(id)
                    },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "棋盘样式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        for ((id, label) in SettingsStore.BOARD_SKINS) {
            FilterChip(
                selected = settings.boardSkin == id,
                onClick = { update(settings.copy(boardSkin = id)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Live board preview with the selected skin.
        val demoGame = remember {
            com.tuxungo.android.go.GoGame(com.tuxungo.android.go.BoardSize(9, 9), 7.5).apply {
                loadPosition(
                    listOf(
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.BLACK, 2, 2),
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.WHITE, 6, 6),
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.BLACK, 6, 2),
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.WHITE, 2, 6),
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.BLACK, 3, 6)
                    ),
                    com.tuxungo.android.go.Stone.WHITE
                )
                play(4, 4)
            }
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.width(240.dp)) {
                BoardView(
                    game = demoGame,
                    enabled = false,
                    skin = BoardSkin.fromId(settings.boardSkin),
                    showCoordinates = settings.showCoordinates
                ) { _, _ -> }
            }
        }
        }

        if (page == "sound") {
        Text(text = "音效", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "开启音效", fontSize = 15.sp)
            Switch(
                checked = settings.soundEnabled,
                onCheckedChange = { update(settings.copy(soundEnabled = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "落子震动", fontSize = 15.sp)
                Text(text = "落子时轻震一下，提子时震两下", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.vibrationEnabled,
                onCheckedChange = { update(settings.copy(vibrationEnabled = it)) }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "落子音效（点选即试听）", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        for ((id, label) in SettingsStore.PLACE_SOUNDS) {
            FilterChip(
                selected = settings.placeSound == id,
                enabled = settings.soundEnabled,
                onClick = {
                    update(settings.copy(placeSound = id))
                    previewPlace(id)
                },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "提子音效（点选即试听）", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        for ((id, label) in SettingsStore.CAPTURE_SOUNDS) {
            FilterChip(
                selected = settings.captureSound == id,
                enabled = settings.soundEnabled,
                onClick = {
                    update(settings.copy(captureSound = id))
                    previewCapture(id)
                },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        }

        if (page == "judge") {
        Text(text = "判断计算量", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "形势判断和终局数目算多深：越大越准，也越慢。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((v, label) in SettingsStore.JUDGE_VISITS) {
                FilterChip(
                    selected = settings.judgeVisits == v,
                    onClick = { update(settings.copy(judgeVisits = v)) },
                    label = { Text(label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "领地着色", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "棋盘上的黑白小方块是引擎判断的领地：颜色越深，归属越确定。拖动下面两个滑杆，预览会实时变。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "蔓延程度", fontSize = 14.sp)
            Text(
                text = "归属 ${(settings.territorySpread * 100).toInt()}% 以上才上色",
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Slider(
            value = settings.territorySpread,
            onValueChange = { update(settings.copy(territorySpread = it)) },
            valueRange = 0.05f..0.6f,
            steps = 10
        )
        Text(
            text = "调高：只有定型的地方才上色，棋盘干净；调低：稍有倾向就上色，看得出势力的走向。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "颜色浓淡", fontSize = 14.sp)
            Text(
                text = "${(settings.territoryIntensity * 100).toInt()}%",
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Slider(
            value = settings.territoryIntensity,
            onValueChange = { update(settings.copy(territoryIntensity = it)) },
            valueRange = 0.4f..1.0f,
            steps = 5
        )
        Spacer(modifier = Modifier.height(8.dp))
        val judgeDemo = remember {
            com.tuxungo.android.go.GoGame(com.tuxungo.android.go.BoardSize(9, 9), 7.5).apply {
                loadPosition(
                    listOf(
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.BLACK, 2, 2),
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.WHITE, 6, 6),
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.BLACK, 6, 2),
                        com.tuxungo.android.go.Move(com.tuxungo.android.go.Stone.WHITE, 2, 6)
                    ),
                    com.tuxungo.android.go.Stone.BLACK
                )
            }
        }
        val judgeOwnership = remember {
            DoubleArray(81) { i ->
                val x = i % 9
                val y = i / 9
                (((4 - x) + (4 - y)) / 8.0 * 1.7 - 0.85).coerceIn(-1.0, 1.0)
            }
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.width(220.dp)) {
                BoardView(
                    game = judgeDemo,
                    enabled = false,
                    skin = BoardSkin.fromId(settings.boardSkin),
                    showCoordinates = false,
                    ownership = judgeOwnership,
                    territorySpread = settings.territorySpread,
                    territoryIntensity = settings.territoryIntensity
                ) { _, _ -> }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "死子判定严格度", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "判断时把一整块棋判死的门槛。保守更少误判，活棋不会被错杀；大胆判得更果断。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))
        for ((id, label) in SettingsStore.DEAD_STRICTNESS) {
            FilterChip(
                selected = settings.deadStrictness == id,
                onClick = { update(settings.copy(deadStrictness = id)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "判断面板显示", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "目数明细", fontSize = 15.sp)
                Text(text = "黑白双方的活子、空、贴目逐项列出", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.judgeShowDetail,
                onCheckedChange = { update(settings.copy(judgeShowDetail = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "得分比例条", fontSize = 15.sp)
                Text(text = "黑白得分占比的横条", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.judgeShowBar,
                onCheckedChange = { update(settings.copy(judgeShowBar = it)) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "可信度提示", fontSize = 15.sp)
                Text(text = "局面还有多少没定型，一行话说明", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.judgeShowConfidence,
                onCheckedChange = { update(settings.copy(judgeShowConfidence = it)) }
            )
        }
        }

        if (page == "data") {
        val libCount = remember {
            com.tuxungo.android.LibraryStore.listGames(context).size
        }
        val libBytes = remember {
            runCatching {
                java.io.File(context.filesDir, "library").walkTopDown()
                    .filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
        }
        Text(text = "数据概览", fontSize = 15.sp)
        Text(
            text = "棋谱库共 $libCount 局，占约 ${libBytes / 1024} KB" +
                "（不含模型文件，模型占用在模型库页看）。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        var showResetConfirm by remember { mutableStateOf(false) }
        androidx.compose.material3.OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("恢复默认设置") }
        if (showResetConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("恢复默认设置？") },
                text = { Text("主题色、棋盘样式、音效、落子方式等会全部回到默认。棋谱库和模型库不受影响。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetConfirm = false
                            update(com.tuxungo.android.AppSettings())
                            onAccentChange("sakura")
                            onThemeModeChange("system")
                        }
                    ) { Text("恢复默认") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "版本更新", fontSize = 15.sp)
        val currentVersion = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
        }
        if (currentVersion.isNotEmpty()) {
            Text(
                text = "当前版本 $currentVersion",
                fontSize = 12.sp, color = Color.Gray
            )
        }
        Text(
            text = "手动检查 GitHub 上有没有新版本；只有点这个按钮时才会联网一次。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))
        var checkingUpdate by remember { mutableStateOf(false) }
        var updateText by remember { mutableStateOf("") }
        val updateScope = rememberCoroutineScope()
        androidx.compose.material3.OutlinedButton(
            onClick = {
                if (checkingUpdate) return@OutlinedButton
                checkingUpdate = true
                updateText = "正在检查…"
                updateScope.launch {
                    val msg = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
                    updateText = msg
                    checkingUpdate = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("检查更新") }
        if (updateText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = updateText, fontSize = 13.sp, color = Color(0xFF2F7CF6))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "数据备份", fontSize = 15.sp)
        Text(
            text = "把棋谱库、训练进度和全部设置打包成一个文件；换手机或重装后，导入即可恢复。导入的模型文件不在备份内（太大），恢复后重新导入模型即可。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(6.dp))
        var backupNote by remember { mutableStateOf("") }
        var pendingImportUri by remember {
            mutableStateOf<android.net.Uri?>(null)
        }
        var showImportConfirm by remember { mutableStateOf(false) }
        val backupScope = rememberCoroutineScope()
        val importLauncher =
            androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts
                    .OpenDocument()
            ) { uri ->
                if (uri != null) {
                    pendingImportUri = uri
                    showImportConfirm = true
                }
            }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                backupScope.launch {
                    val file = withContext(Dispatchers.IO) {
                        com.tuxungo.android.BackupStore
                            .exportToFile(context)
                    }
                    val uri = androidx.core.content.FileProvider
                        .getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_SEND
                    ).apply {
                        type = "application/json"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(
                            android.content.Intent
                                .FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(
                            intent, "导出备份"
                        )
                    )
                    backupNote = "备份文件已生成，选一个地方保存好。"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导出备份") }
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导入恢复") }
        if (backupNote.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = backupNote, fontSize = 13.sp, color = Color(0xFF2F7CF6))
        }
        if (showImportConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showImportConfirm = false },
                title = { Text("恢复备份？") },
                text = {
                    Text("恢复会覆盖当前的棋谱库、训练进度和设置。确定要恢复吗？")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showImportConfirm = false
                            val uri = pendingImportUri
                            if (uri != null) {
                                backupNote = "正在恢复…"
                                backupScope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val text = context.contentResolver
                                                .openInputStream(uri)
                                                ?.bufferedReader()
                                                ?.readText()
                                                ?: error("读不到这个文件")
                                            com.tuxungo.android.BackupStore
                                                .importJson(context, text)
                                        }
                                    }
                                    backupNote = result.fold(
                                        onSuccess = {
                                            "$it。请重启 App，让设置全部生效。"
                                        },
                                        onFailure = {
                                            "恢复失败：${it.message ?: "文件不对"}"
                                        }
                                    )
                                }
                            }
                        }
                    ) { Text("恢复") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showImportConfirm = false }
                    ) { Text("取消") }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "设置修改后，对正在进行的对局将在下一局生效。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "「真实录音」落子/提子音效来自开源围棋软件 Sabaki（MIT 授权），为真棋盘实录。",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "引擎：KataGo 1.15.3（GTP / 分析协议）· 内置模型：kata1-b10c128（10 层）· 对局与分析完全离线；只有手动检查更新时才联网一次。",
            fontSize = 12.sp, color = Color.Gray
        )
        }
    }
}
