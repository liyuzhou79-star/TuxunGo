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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onAccentChange: (String) -> Unit = {}
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "设置", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(modifier = Modifier.height(16.dp))

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
                Text(text = "对局时屏幕常亮", fontSize = 15.sp)
                Text(text = "长考和观战 AI 对决时屏幕不熄灭", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = settings.keepScreenOn,
                onCheckedChange = { update(settings.copy(keepScreenOn = it)) }
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
        Spacer(modifier = Modifier.height(16.dp))

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
                        }
                    ) { Text("恢复默认") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
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
            text = "引擎：KataGo 1.15.3（GTP / 分析协议）· 内置模型：kata1-b10c128（10 层）· 完全离线运行，不联网。",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}
