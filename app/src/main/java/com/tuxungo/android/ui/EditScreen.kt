package com.tuxungo.android.ui

import android.widget.Toast
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import com.tuxungo.android.SettingsStore
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone

private enum class EditTool { BLACK, WHITE, ERASE }

/**
 * Free position editor （摆棋）: place black/white stones anywhere, then
 * start a game from the position — against the AI on either side,
 * two-player, or AI vs AI.
 */
@Composable
fun EditScreen(width: Int, height: Int, onStart: (GameConfig) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }
    var stones by remember { mutableStateOf<Map<Pair<Int, Int>, Stone>>(emptyMap()) }
    var tool by remember { mutableStateOf(EditTool.BLACK) }
    var startPlayer by remember { mutableStateOf(Stone.BLACK) }
    var version by remember { mutableIntStateOf(0) }
    var showStartDialog by remember { mutableStateOf(false) }

    // A fresh GoGame mirroring the edited stones, for display only.
    val game = remember(stones, width, height) {
        val g = GoGame(BoardSize(width, height), 7.5)
        val list = stones.map { (p, s) -> Move(s, p.first, p.second) }
        g.loadPosition(list, startPlayer)
        g
    }
    // Live validity: any group without liberties cannot start a game.
    val positionValid = remember(stones, width, height) {
        val probe = GoGame(BoardSize(width, height), 7.5)
        probe.loadPosition(stones.map { (p, s) -> Move(s, p.first, p.second) }, startPlayer)
    }
    @Suppress("UNUSED_EXPRESSION") version

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "摆棋", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(
            text = "选好棋子颜色后点棋盘放子；点已有的子可以拿掉。",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        BoardView(
            game = game,
            enabled = true,
            skin = BoardSkin.fromId(settings.boardSkin),
            showCoordinates = settings.showCoordinates
        ) { x, y ->
            val key = x to y
            val existing = stones[key]
            stones = when (tool) {
                EditTool.ERASE -> stones - key
                EditTool.BLACK ->
                    if (existing == Stone.BLACK) stones - key else stones + (key to Stone.BLACK)
                EditTool.WHITE ->
                    if (existing == Stone.WHITE) stones - key else stones + (key to Stone.WHITE)
            }
            version++
        }
        if (stones.isNotEmpty() && !positionValid) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "有棋块没有气（活不了），这样摆开不了局，请先调整。",
                fontSize = 13.sp, color = Color(0xFFC62828)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tool == EditTool.BLACK, onClick = { tool = EditTool.BLACK }, label = { Text("放黑子") })
            FilterChip(selected = tool == EditTool.WHITE, onClick = { tool = EditTool.WHITE }, label = { Text("放白子") })
            FilterChip(selected = tool == EditTool.ERASE, onClick = { tool = EditTool.ERASE }, label = { Text("拿子") })
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "轮到：", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                FilterChip(selected = startPlayer == Stone.BLACK, onClick = { startPlayer = Stone.BLACK }, label = { Text("黑下") })
                FilterChip(selected = startPlayer == Stone.WHITE, onClick = { startPlayer = Stone.WHITE }, label = { Text("白下") })
            }
            TextButton(onClick = { stones = emptyMap(); version++ }) { Text("清空") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val list = stones.map { (p, s) -> Move(s, p.first, p.second) }
                val probe = GoGame(BoardSize(width, height), 7.5)
                if (!probe.loadPosition(list, startPlayer)) {
                    Toast.makeText(context, "有棋块没有气（活不了），请先调整再开始", Toast.LENGTH_LONG).show()
                } else {
                    showStartDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("摆好了，开始对弈") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("取消") }
    }

    if (showStartDialog) {
        val stoneList = stones.map { (p, s) -> Move(s, p.first, p.second) }
        fun cfg(mode: GameMode, human: Stone) = GameConfig(
            mode = mode,
            width = width,
            height = height,
            handicap = 0,
            komi = 7.5,
            humanStone = human,
            aiVisits = 200,
            customStones = stoneList,
            startPlayer = startPlayer
        )
        AlertDialog(
            onDismissRequest = { showStartDialog = false },
            title = { Text("谁来下？") },
            text = { Text("从这个局面开始，选对弈方式（AI 可以接手任意一边）：") },
            confirmButton = {
                TextButton(onClick = { showStartDialog = false; onStart(cfg(GameMode.EVE, Stone.BLACK)) }) {
                    Text("AI 对 AI")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showStartDialog = false; onStart(cfg(GameMode.PVE, Stone.BLACK)) }) {
                        Text("我执黑")
                    }
                    TextButton(onClick = { showStartDialog = false; onStart(cfg(GameMode.PVE, Stone.WHITE)) }) {
                        Text("我执白")
                    }
                    TextButton(onClick = { showStartDialog = false; onStart(cfg(GameMode.PVP, Stone.BLACK)) }) {
                        Text("双人")
                    }
                }
            }
        )
    }
}
