package com.tuxungo.android.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.tuxungo.android.LibraryFolder
import com.tuxungo.android.LibraryGame
import com.tuxungo.android.LibraryStore
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Stone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Folder picker used when saving a game into the library. */
@Composable
fun SaveGameDialog(
    game: LibraryGame,
    onDismiss: () -> Unit,
    onSaved: (LibraryGame) -> Unit
) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf(LibraryStore.listFolders(context)) }
    var newFolderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏棋谱") },
        text = {
            Column {
                Text("选一个文件夹放这局棋：", fontSize = 14.sp)
                TextButton(
                    onClick = {
                        onSaved(LibraryStore.saveGame(context, game.copy(folderId = null)))
                    }
                ) { Text("未分类") }
                for (f in folders) {
                    TextButton(
                        onClick = {
                            onSaved(LibraryStore.saveGame(context, game.copy(folderId = f.id)))
                        }
                    ) { Text(f.name) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("新文件夹名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            val f = LibraryStore.createFolder(context, newFolderName)
                            folders = LibraryStore.listFolders(context)
                            onSaved(LibraryStore.saveGame(context, game.copy(folderId = f.id)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("新建文件夹并收藏") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** The game library: folders of saved games, tap one to review it. */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenGame: (LibraryGame) -> Unit
) {
    val context = LocalContext.current
    var folders by remember {
        mutableStateOf<List<com.tuxungo.android.LibraryFolder>>(emptyList())
    }
    var games by remember {
        mutableStateOf<List<com.tuxungo.android.LibraryGame>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    // "ALL" shows everything, null shows uncategorized, else a folder id.
    var selected by remember { mutableStateOf<String?>("ALL") }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<LibraryGame?>(null) }
    var deleteTarget by remember { mutableStateOf<LibraryGame?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LibraryGame?>(null) }
    var renameText by remember { mutableStateOf("") }
    var exportedNote by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    fun buildSgf(g: LibraryGame): String {
        val setup = when {
            g.customStones.isNotEmpty() -> g.customStones
            g.handicap > 0 -> {
                val tmp = com.tuxungo.android.go.GoGame(g.size, g.komi)
                tmp.setupHandicap(g.handicap)
                val out = mutableListOf<com.tuxungo.android.go.Move>()
                for (y in 0 until g.size.height) {
                    for (x in 0 until g.size.width) {
                        tmp.stoneAt(x, y)?.let {
                            out.add(com.tuxungo.android.go.Move(it, x, y))
                        }
                    }
                }
                out
            }
            else -> emptyList()
        }
        val replay = com.tuxungo.android.go.GoGame(g.size, g.komi)
        when {
            g.customStones.isNotEmpty() -> replay.loadPosition(g.customStones, g.startPlayer)
            g.handicap > 0 -> replay.setupHandicap(g.handicap)
        }
        for (m in g.moves) {
            if (m.isPass) replay.pass() else replay.play(m.x, m.y)
        }
        val startPlayer = when {
            g.customStones.isNotEmpty() -> g.startPlayer
            g.handicap > 0 -> com.tuxungo.android.go.Stone.WHITE
            else -> com.tuxungo.android.go.Stone.BLACK
        }
        return com.tuxungo.android.go.Sgf.export(
            replay,
            setupStones = setup,
            startPlayer = startPlayer
        )
    }

    fun exportSgf(g: LibraryGame) {
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(buildSgf(g)))
        exportedNote = "已复制「${g.title}」的 SGF 到剪贴板"
    }

    /** Export the whole library as one multi-game SGF file. */
    fun exportAllSgf() {
        if (games.isEmpty()) {
            exportedNote = "棋谱库是空的，还没有可导出的棋谱"
            return
        }
        runCatching {
            val all = games.joinToString("\n") { buildSgf(it) }
            val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            val file = java.io.File(dir, "tuxungo_library.sgf")
            file.writeText(all)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            val intent = android.content.Intent(
                android.content.Intent.ACTION_SEND
            ).apply {
                type = "application/x-go-sgf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "导出全部棋谱")
            )
            exportedNote = "正在导出全部 ${games.size} 局棋谱"
        }.onFailure {
            exportedNote = "导出失败：${it.message ?: "未知错误"}"
        }
    }
    var deleteFolderTarget by remember { mutableStateOf<String?>(null) }
    var renameFolderTarget by remember { mutableStateOf<LibraryFolder?>(null) }
    var renameFolderText by remember { mutableStateOf("") }
    var clearAutoTarget by remember { mutableStateOf<LibraryFolder?>(null) }

    fun refresh() {
        folders = LibraryStore.listFolders(context)
        games = LibraryStore.listGames(context)
    }

    // Load off the main thread: a big library no longer stalls the
    // screen opening; skeleton cards show while it loads.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            refresh()
        }
        loading = false
    }

    var searchText by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("new") }
    var resultFilter by remember { mutableStateOf("ALL") }
    val shown = games.filter { g ->
        val inFolder = when (selected) {
            "ALL" -> true
            null -> g.folderId == null
            else -> g.folderId == selected
        }
        val q = searchText.trim()
        val folderName = g.folderId?.let { fid ->
            folders.firstOrNull { it.id == fid }?.name
        } ?: "未分类"
        val inSearch = q.isEmpty() ||
            g.title.contains(q, ignoreCase = true) ||
            g.modeLabel.contains(q, ignoreCase = true) ||
            folderName.contains(q, ignoreCase = true)
        val inResult = when (resultFilter) {
            "B" -> g.resultText.startsWith("黑")
            "W" -> g.resultText.startsWith("白")
            else -> true
        }
        inFolder && inSearch && inResult
    }.let { list ->
        when (sortMode) {
            "old" -> list.sortedBy { it.createdAt }
            "long" -> list.sortedByDescending { it.moves.size }
            "short" -> list.sortedBy { it.moves.size }
            else -> list.sortedByDescending { it.createdAt }
        }
    }
    val dateFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { exportAllSgf() }) { Text("导出全部") }
            TextButton(onClick = { showNewFolder = true }) { Text("新建文件夹") }
        }
        Text("棋谱库", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "收藏的对局都在这里，点开就能复盘。AI 对局、双人对局、摆棋局都能存。",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("搜索棋谱（标题或类型）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = selected == "ALL",
                onClick = { selected = "ALL" },
                label = { Text("全部 ${games.size}") }
            )
            for (f in folders) {
                Spacer(modifier = Modifier.padding(start = 6.dp))
                FilterChip(
                    selected = selected == f.id,
                    onClick = { selected = f.id },
                    label = { Text("${f.name} ${games.count { it.folderId == f.id }}") }
                )
            }
            Spacer(modifier = Modifier.padding(start = 6.dp))
            FilterChip(
                selected = selected == null,
                onClick = { selected = null },
                label = { Text("未分类 ${games.count { it.folderId == null }}") }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("排序", fontSize = 13.sp, color = Color.Gray)
            for ((id, label) in listOf(
                "new" to "最新",
                "old" to "最早",
                "long" to "手数多",
                "short" to "手数少"
            )) {
                FilterChip(
                    selected = sortMode == id,
                    onClick = { sortMode = id },
                    label = { Text(label) }
                )
            }
        }
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("结果", fontSize = 13.sp, color = Color.Gray)
            for ((id, label) in listOf(
                "ALL" to "全部",
                "B" to "黑胜",
                "W" to "白胜"
            )) {
                FilterChip(
                    selected = resultFilter == id,
                    onClick = { resultFilter = id },
                    label = { Text(label) }
                )
            }
        }
        val selectedFolder = folders.firstOrNull { it.id == selected }
        if (selectedFolder != null) {
            Row {
                TextButton(
                    onClick = {
                        renameFolderTarget = selectedFolder
                        renameFolderText = selectedFolder.name
                    }
                ) { Text("重命名文件夹", fontSize = 12.sp) }
                TextButton(onClick = { deleteFolderTarget = selectedFolder.id }) {
                    Text("删除文件夹（棋谱移到未分类）", fontSize = 12.sp)
                }
                if (selectedFolder.name == "自动保存") {
                    TextButton(onClick = { clearAutoTarget = selectedFolder }) {
                        Text("清空自动保存", fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (!selectionMode) {
                TextButton(
                    onClick = {
                        selectionMode = true
                        selectedIds = emptySet()
                    },
                    enabled = shown.isNotEmpty()
                ) { Text("多选删除", fontSize = 12.sp) }
            } else {
                Text(
                    text = "已选 ${selectedIds.size} 项",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    selectedIds = shown.map { it.id }.toSet()
                }) { Text("全选", fontSize = 12.sp) }
                TextButton(
                    onClick = { showBatchDelete = true },
                    enabled = selectedIds.isNotEmpty()
                ) { Text("删除所选", fontSize = 12.sp) }
                TextButton(onClick = {
                    selectionMode = false
                    selectedIds = emptySet()
                }) { Text("取消", fontSize = 12.sp) }
            }
        }

        if (exportedNote.isNotEmpty()) {
            Text(exportedNote, fontSize = 13.sp, color = Color(0xFF2F7CF6))
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (loading) {
            SkeletonCards()
        } else if (shown.isEmpty()) {
            EmptyState(
                title = if (games.isEmpty()) "还没有棋谱" else "这里是空的",
                desc = if (games.isEmpty()) {
                    "对局结束后会自动存进来，也可以在复盘页点「收藏棋谱」。"
                } else {
                    "这个文件夹或搜索下还没有棋谱，换个条件看看。"
                }
            )
        }
        for (g in shown) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (selectionMode) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = g.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + g.id
                                    } else {
                                        selectedIds - g.id
                                    }
                                }
                            )
                            Column {
                                Text(g.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${g.moves.size} 手 · ${dateFmt.format(Date(g.createdAt))}",
                                    fontSize = 12.sp, color = Color.Gray
                                )
                            }
                        }
                    } else {
                    Row {
                        GameThumb(
                            g,
                            modifier = Modifier.size(62.dp)
                                .clickable { onOpenGame(g) }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(g.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${g.modeLabel} · ${g.resultText.ifBlank { "无结果" }} · ${g.moves.size} 手 · ${dateFmt.format(Date(g.createdAt))}",
                                fontSize = 12.sp, color = Color.Gray
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { onOpenGame(g) }) { Text("打开复盘") }
                        TextButton(
                            onClick = {
                                renameTarget = g
                                renameText = g.title
                            }
                        ) { Text("改名") }
                        TextButton(onClick = { moveTarget = g }) { Text("移动") }
                        TextButton(
                            onClick = {
                                LibraryStore.duplicateGame(context, g)
                                refresh()
                            }
                        ) { Text("复制") }
                        TextButton(onClick = { exportSgf(g) }) { Text("导出") }
                        TextButton(onClick = { deleteTarget = g }) { Text("删除") }
                    }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("文件夹名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            LibraryStore.createFolder(context, newFolderName)
                            newFolderName = ""
                            refresh()
                        }
                        showNewFolder = false
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("取消") }
            }
        )
    }

    if (clearAutoTarget != null) {
        val target = clearAutoTarget!!
        val count = games.count { it.folderId == target.id }
        AlertDialog(
            onDismissRequest = { clearAutoTarget = null },
            title = { Text("清空自动保存？") },
            text = {
                Text("「自动保存」里的 $count 份棋谱会被删除，收藏到其他文件夹的棋谱不受影响。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        for (g in games.filter { it.folderId == target.id }) {
                            LibraryStore.deleteGame(context, g.id)
                        }
                        clearAutoTarget = null
                        refresh()
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { clearAutoTarget = null }) { Text("取消") }
            }
        )
    }

    if (renameFolderTarget != null) {
        val target = renameFolderTarget!!
        AlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            title = { Text("重命名文件夹") },
            text = {
                OutlinedTextField(
                    value = renameFolderText,
                    onValueChange = { renameFolderText = it },
                    label = { Text("文件夹名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LibraryStore.renameFolder(context, target.id, renameFolderText)
                        renameFolderTarget = null
                        refresh()
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameFolderTarget = null }) { Text("取消") }
            }
        )
    }

    if (renameTarget != null) {
        val target = renameTarget!!
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("给棋谱改名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            LibraryStore.renameGame(context, target.id, renameText.trim())
                            refresh()
                        }
                        renameTarget = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    if (moveTarget != null) {
        val target = moveTarget!!
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("移动到") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            LibraryStore.moveToFolder(context, target.id, null)
                            moveTarget = null
                            refresh()
                        }
                    ) { Text("未分类") }
                    for (f in folders) {
                        TextButton(
                            onClick = {
                                LibraryStore.moveToFolder(context, target.id, f.id)
                                moveTarget = null
                                refresh()
                            }
                        ) { Text(f.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveTarget = null }) { Text("取消") }
            }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这局棋谱？") },
            text = { Text(target.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LibraryStore.deleteGame(context, target.id)
                        deleteTarget = null
                        refresh()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text("删除所选棋谱？") },
            text = { Text("将删除 ${selectedIds.size} 局棋谱，删了不能恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        for (id in selectedIds) {
                            LibraryStore.deleteGame(context, id)
                        }
                        showBatchDelete = false
                        selectionMode = false
                        selectedIds = emptySet()
                        refresh()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDelete = false }) { Text("取消") }
            }
        )
    }

    if (deleteFolderTarget != null) {
        val fid = deleteFolderTarget!!
        val fname = folders.firstOrNull { it.id == fid }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text("删除文件夹「$fname」？") },
            text = { Text("文件夹里的棋谱不会被删，会移到「未分类」。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        LibraryStore.deleteFolder(context, fid)
                        deleteFolderTarget = null
                        selected = "ALL"
                        refresh()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolderTarget = null }) { Text("取消") }
            }
        )
    }
}

/** Grey placeholder cards while the library loads off-thread. */
@Composable
private fun SkeletonCards() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(
        label = "skeletonPulse"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Column {
        repeat(3) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(15.dp)
                            .background(
                                Color.LightGray.copy(alpha = alpha),
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(12.dp)
                            .background(
                                Color.LightGray.copy(alpha = alpha),
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

/** Rendered final-position thumbnails, cached per game id. */
private val thumbCache = HashMap<String, android.graphics.Bitmap>()

@Composable
private fun GameThumb(g: LibraryGame, modifier: Modifier = Modifier) {
    val bmp = remember(g.id) { thumbCache.getOrPut(g.id) { renderThumb(g) } }
    androidx.compose.foundation.Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        modifier = modifier
    )
}

/** Draws the game's final position on a small wood-coloured bitmap. */
private fun renderThumb(g: LibraryGame): android.graphics.Bitmap {
    val px = 192
    val bmp = android.graphics.Bitmap.createBitmap(
        px, px, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.rgb(227, 183, 120)
    canvas.drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint)
    val game = GoGame(g.size, g.komi)
    if (g.customStones.isNotEmpty()) {
        game.loadPosition(g.customStones, g.startPlayer)
    }
    for (m in g.moves) {
        if (m.isPass) game.pass() else game.play(m.x, m.y)
    }
    val w = g.size.width
    val h = g.size.height
    val maxDim = maxOf(w, h).coerceAtLeast(2)
    val cell = (px * 0.86f) / (maxDim - 1)
    val boardW = cell * (w - 1)
    val boardH = cell * (h - 1)
    val ox = (px - boardW) / 2f
    val oy = (px - boardH) / 2f
    paint.color = android.graphics.Color.rgb(90, 60, 30)
    paint.strokeWidth = 1.1f
    for (x in 0 until w) {
        canvas.drawLine(ox + x * cell, oy, ox + x * cell, oy + boardH, paint)
    }
    for (y in 0 until h) {
        canvas.drawLine(ox, oy + y * cell, ox + boardW, oy + y * cell, paint)
    }
    val r = cell * 0.46f
    for (y in 0 until h) {
        for (x in 0 until w) {
            val s = game.stoneAt(x, y) ?: continue
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = if (s == Stone.BLACK) {
                android.graphics.Color.rgb(28, 28, 28)
            } else {
                android.graphics.Color.rgb(246, 246, 246)
            }
            canvas.drawCircle(ox + x * cell, oy + y * cell, r, paint)
            if (s == Stone.WHITE) {
                paint.style = android.graphics.Paint.Style.STROKE
                paint.color = android.graphics.Color.rgb(150, 150, 150)
                canvas.drawCircle(ox + x * cell, oy + y * cell, r, paint)
            }
        }
    }
    return bmp
}
