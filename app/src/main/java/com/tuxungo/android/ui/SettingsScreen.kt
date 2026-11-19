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
fun SettingsScreen(onBack: () -> Unit) {
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

    fun previewPlace(id: String) {
        val res = SettingsStore.placeSoundRes(id)
        val sid = previewPool.load(context, res, 1)
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

    fun previewCapture(id: String) {
        val res = SettingsStore.captureSoundRes(id)
        val sid = previewPool.load(context, res, 1)
        Thread {
            var tries = 0
            while (tries < 20) {
                if (previewPool.play(sid, 0.9f, 0.9f, 1, 0, 1f) != 0) break
                Thread.sleep(50)
                tries++
            }
        }.start()
    }

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
        Text(
            text = "设置修改后，对正在进行的对局将在下一局生效。",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}
