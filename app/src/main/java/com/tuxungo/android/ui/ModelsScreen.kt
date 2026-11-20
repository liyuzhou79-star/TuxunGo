package com.tuxungo.android.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungo.android.engine.EngineAssets
import com.tuxungo.android.engine.KataEngine
import com.tuxungo.android.engine.ModelInfo
import com.tuxungo.android.engine.ModelStore
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.Stone
import java.util.concurrent.Executors

@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") refresh
    val models = remember(refresh) { ModelStore.list(context) }
    val activeId = ModelStore.activeId(context)
    var importing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ModelInfo?>(null) }
    var deleteTarget by remember { mutableStateOf<ModelInfo?>(null) }
    var showStrongDownload by remember { mutableStateOf(false) }
    val speedPrefs = remember {
        context.getSharedPreferences("model_speed", android.content.Context.MODE_PRIVATE)
    }
    var speedResults by remember {
        mutableStateOf(
            models.mapNotNull { m ->
                speedPrefs.getString("speed_${m.id}", null)?.let { m.id to it }
            }.toMap()
        )
    }
    var testingId by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var importName by remember { mutableStateOf("") }
    val importExecutor = remember { Executors.newSingleThreadExecutor() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
        importName = raw.substringBeforeLast('.').ifBlank { raw }
        pendingImport = uri
    }

    fun startImport(uri: Uri, chosenName: String) {
        if (ModelStore.userCount(context) >= 99) {
            statusText = "模型库已满（最多 99 个），请先删除不用的模型。"
            return
        }
        val raw = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
        importing = true
        statusText = "正在校验并实测加载…"
        importExecutor.execute {
            val result = ModelStore.importModel(
                context, uri, raw, chosenName.ifBlank { null }
            )
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                importing = false
                when (result) {
                    is ModelStore.ImportResult.Ok -> {
                        statusText = "已加入模型库：${result.info.displayName}"
                        refresh++
                    }
                    is ModelStore.ImportResult.Failed -> {
                        statusText = result.reason
                    }
                }
            }
        }
    }

    // Speed test, node-test style: spin up a throwaway engine on
    // this model, warm it up with one move, then time a full
    // 200-visit move on an empty 19x19 board and report visits/sec.
    fun runSpeedTest(model: ModelInfo) {
        if (testingId != null) return
        testingId = model.id
        importExecutor.execute {
            val text = try {
                val file = ModelStore.resolveFile(context, model.id)
                val dir = java.io.File(
                    EngineAssets.engineWorkDir(context), "speedtest"
                ).apply { mkdirs() }
                val visits = 200
                val eng = KataEngine.start(
                    context.applicationInfo.nativeLibraryDir, dir, file, visits
                )
                try {
                    val size = BoardSize(19, 19)
                    eng.setBoardSize(size)
                    eng.setKomi(7.5)
                    eng.clearBoard()
                    val first = eng.genmove(Stone.BLACK, size)
                    if (first != null) eng.play(first, size)
                    val t0 = System.nanoTime()
                    eng.genmove(Stone.WHITE, size)
                    val secs = (System.nanoTime() - t0) / 1e9
                    val vps = (visits / secs).toInt()
                    "思考速度约 $vps 访问/秒（19 路空盘 · 200 访问/手）"
                } finally {
                    eng.quit()
                }
            } catch (e: Exception) {
                "测速失败：${e.message ?: "引擎没能启动"}"
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                speedResults = speedResults + (model.id to text)
                if (text.startsWith("思考速度")) {
                    speedPrefs.edit().putString("speed_${model.id}", text).apply()
                }
                testingId = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "模型库", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("‹ 返回") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "可上传 KataGo 官方模型文件（.txt.gz 或 .bin.gz）。上传后会先校验文件完整性、" +
                "解析模型结构，再用引擎实测加载，能通过才加入模型库。最多可添加 99 个模型。" +
                "强力模型改为免费按需下载；所有模型（包括预装的）都能删除腾空间，预装的删除后可免费恢复。",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "已装模型共占约 ${models.filter { it.installed }.sumOf { it.sizeBytes } / 1_000_000} MB",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = !importing,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (importing) "正在校验…" else "上传模型文件") }
        if (statusText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = statusText, fontSize = 13.sp, color = Color(0xFF2F7CF6))
        }
        Spacer(modifier = Modifier.height(16.dp))

        for (model in models) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = model.displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (model.id == activeId) {
                            Text(text = "使用中", fontSize = 13.sp, color = Color(0xFF2F7CF6))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${model.archText} · ${formatSize(model.sizeBytes)}",
                        fontSize = 13.sp, color = Color.Gray
                    )
                    speedResults[model.id]?.let { speed ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = speed, fontSize = 13.sp, color = Color(0xFF2F7CF6))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            model.id == ModelStore.STRONG_ID && !model.installed -> {
                                Button(onClick = { showStrongDownload = true }) {
                                    Text("下载强力模型")
                                }
                                OutlinedButton(onClick = { renameTarget = model }) { Text("改名") }
                            }
                            !model.installed -> {
                                OutlinedButton(onClick = {
                                    ModelStore.restore(context, model.id)
                                    refresh++
                                    Toast.makeText(context, "已恢复：${model.displayName}", Toast.LENGTH_SHORT).show()
                                }) { Text("恢复") }
                                OutlinedButton(onClick = { renameTarget = model }) { Text("改名") }
                            }
                            else -> {
                                if (model.id != activeId && !model.isHumanOnly) {
                                    OutlinedButton(onClick = {
                                        ModelStore.setActive(context, model.id)
                                        refresh++
                                        Toast.makeText(context, "已切换为：${model.displayName}", Toast.LENGTH_SHORT).show()
                                    }) { Text("使用") }
                                }
                                if (!model.isHumanOnly) {
                                    OutlinedButton(
                                        onClick = { runSpeedTest(model) },
                                        enabled = testingId == null
                                    ) { Text(if (testingId == model.id) "测速中…" else "测速") }
                                }
                                OutlinedButton(onClick = { renameTarget = model }) { Text("改名") }
                                OutlinedButton(onClick = { deleteTarget = model }) { Text("删除") }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("给模型起个名字") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        singleLine = true,
                        label = { Text("模型名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "这个名字会显示在模型库和对局的 AI 托管设置里。",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = importName.trim()
                    pendingImport = null
                    startImport(uri, chosen)
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            }
        )
    }

    renameTarget?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("模型改名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        ModelStore.rename(context, target.id, newName.trim())
                        refresh++
                    }
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除模型？") },
            text = {
                Text(
                    when {
                        target.id == ModelStore.STRONG_ID ->
                            "「${target.displayName}」将被删除，腾出约 98 MB。要再用时需要重新下载。"
                        target.isBundled ->
                            "「${target.displayName}」将被删除腾出空间。预装模型随时可以在这里免费恢复，不用联网。"
                        else ->
                            "「${target.displayName}」将被删除，文件不可恢复。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val problem = ModelStore.delete(context, target.id)
                    if (problem != null) {
                        statusText = problem
                    } else {
                        refresh++
                    }
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (showStrongDownload) {
        StrongModelDialog(
            onDismiss = { showStrongDownload = false },
            onChanged = { refresh++ }
        )
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1) String.format("%.1f MB", mb) else "${bytes / 1000} KB"
}
