package com.tuxungo.android.ui

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
import com.tuxungo.android.engine.ModelInfo
import com.tuxungo.android.engine.ModelStore
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
    val importExecutor = remember { Executors.newSingleThreadExecutor() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "model"
        importing = true
        statusText = "正在校验并实测加载「$name」…"
        importExecutor.execute {
            val result = ModelStore.importModel(context, uri, name)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "模型库", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "可上传 KataGo 官方模型文件（.txt.gz 或 .bin.gz）。上传后会先校验文件完整性、" +
                "解析模型结构，再用引擎实测加载，能通过才加入模型库。",
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (model.id != activeId) {
                            OutlinedButton(onClick = {
                                ModelStore.setActive(context, model.id)
                                refresh++
                                Toast.makeText(context, "已切换为：${model.displayName}", Toast.LENGTH_SHORT).show()
                            }) { Text("使用") }
                        }
                        if (!model.isBundled) {
                            OutlinedButton(onClick = { renameTarget = model }) { Text("改名") }
                            OutlinedButton(onClick = { deleteTarget = model }) { Text("删除") }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
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
            text = { Text("「${target.displayName}」将被删除，文件不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    ModelStore.delete(context, target.id)
                    refresh++
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1) String.format("%.1f MB", mb) else "${bytes / 1000} KB"
}
