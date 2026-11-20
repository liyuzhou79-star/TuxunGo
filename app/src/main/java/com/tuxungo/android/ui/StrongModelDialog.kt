package com.tuxungo.android.ui

import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuxungo.android.engine.ModelDownload
import com.tuxungo.android.engine.ModelStore

private fun fmtMb(bytes: Long): String = String.format("%.0f MB", bytes / 1_000_000.0)

private fun fmtSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSecond / 1_000_000.0)
    bytesPerSecond >= 1_000 -> "${bytesPerSecond / 1_000} KB/s"
    else -> "$bytesPerSecond B/s"
}

/**
 * Full-screen sheet for the strong model: what it is good and bad at,
 * exactly how much space it takes, how much room the phone has, then
 * the download itself with progress and a cancel button. When the
 * model is already installed it offers deletion instead.
 */
@Composable
fun StrongModelDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val installed = remember { ModelStore.list(context).firstOrNull { it.id == ModelStore.STRONG_ID }?.installed == true }
    var phase by remember { mutableStateOf(if (installed) "installed" else "info") }
    var downloaded by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val freeBytes = remember {
        try {
            StatFs(context.filesDir.path).availableBytes
        } catch (e: Exception) {
            -1L
        }
    }
    val needBytes = ModelStore.STRONG_SIZE_BYTES
    val enoughSpace = freeBytes < 0 || freeBytes > needBytes + 200_000_000L

    fun startDownload() {
        phase = "downloading"
        downloaded = 0L
        speed = 0L
        ModelDownload.start(
            context,
            onProgress = { d, _, s ->
                downloaded = d
                speed = s
            },
            onDone = { ok, msg ->
                if (ok) {
                    phase = "done"
                    onChanged()
                } else {
                    message = msg
                    phase = if (msg.startsWith("已取消")) "info" else "error"
                }
            }
        )
    }

    Dialog(
        onDismissRequest = {
            if (phase == "downloading") ModelDownload.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(text = "强力模型", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "KataGo 18 层大模型（kata1-b18c384nbt），官方模型库里口碑最好的一档。",
                    fontSize = 13.sp, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(20.dp))

                Text(text = "好处", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "· 形势判断和终局数目明显更准：复杂对杀、死活不清的局面，轻量模型经常拿不准，它大多能判对\n" +
                        "· 棋力更强：AI 对手更难缠，复盘和猜一选的参考着法更可靠\n" +
                        "· 一次下载，之后完全离线使用",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "坏处", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "· 占空间：下载和存放都要约 ${fmtMb(needBytes)}\n" +
                        "· 思考更慢：同样的思考量，它比轻量模型慢十倍左右；日常对局建议把它的思考档位调低一档\n" +
                        "· 下载费流量：约 ${fmtMb(needBytes)}，建议连 WiFi 再下",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "空间占用", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "模型大小：约 ${fmtMb(needBytes)}\n" +
                        if (freeBytes >= 0) {
                            "手机当前可用空间：约 ${fmtMb(freeBytes)}" +
                                if (enoughSpace) "" else "\n空间可能不够（建议至少留出 ${fmtMb(needBytes + 200_000_000L)}），请先清一清手机再下载。"
                        } else {
                            "手机可用空间：读取失败"
                        },
                    fontSize = 14.sp,
                    color = if (enoughSpace) Color.Unspecified else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.height(24.dp))

                when (phase) {
                    "info" -> {
                        Button(
                            onClick = { startDownload() },
                            enabled = enoughSpace,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("开始下载（约 ${fmtMb(needBytes)}）", fontSize = 16.sp) }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("以后再说") }
                    }
                    "downloading" -> {
                        val frac = (downloaded.toDouble() / needBytes).coerceIn(0.0, 1.0)
                        LinearProgressIndicator(
                            progress = { frac.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已下载 ${fmtMb(downloaded)} / ${fmtMb(needBytes)}（${(frac * 100).toInt()}%）· ${fmtSpeed(speed)}",
                            fontSize = 13.sp, color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                ModelDownload.cancel()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("取消下载") }
                    }
                    "done" -> {
                        Text(
                            text = "下载完成！现在可以在模型库里把对局模型换成强力模型，" +
                                "也可以在对局的 AI 托管面板里给某一方单独指定它。",
                            fontSize = 14.sp, color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("完成", fontSize = 16.sp) }
                    }
                    "error" -> {
                        Text(text = message, fontSize = 14.sp, color = Color(0xFFC62828))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { startDownload() },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("重试", fontSize = 16.sp) }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("关闭") }
                    }
                    "installed" -> {
                        Text(
                            text = "强力模型已经在手机里了（约 ${fmtMb(needBytes)}），正在为对局和分析服务。",
                            fontSize = 14.sp, color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!confirmDelete) {
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("删除强力模型，腾出空间") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("完成", fontSize = 16.sp) }
                        } else {
                            Text(
                                text = "删掉后要再用得重新下载（约 ${fmtMb(needBytes)}）。确定删除吗？",
                                fontSize = 14.sp, color = Color(0xFFC62828)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    ModelStore.delete(context, ModelStore.STRONG_ID)
                                    onChanged()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("确定删除", fontSize = 16.sp) }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { confirmDelete = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("不删了") }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = {
                    if (phase == "downloading") ModelDownload.cancel()
                    onDismiss()
                }) { Text("‹ 关闭") }
            }
        }
    }
}
