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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.tuxungo.android.go.BoardSize
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Sgf
import com.tuxungo.android.go.Stone
import java.io.File

@Composable
fun GameScreen(config: GameConfig, onExit: () -> Unit) {
    val context = LocalContext.current
    val game = remember(config) {
        GoGame(BoardSize(config.width, config.height), config.komi).also {
            if (config.handicap > 0) it.setupHandicap(config.handicap)
        }
    }
    var version by remember { mutableIntStateOf(0) }
    var showResignConfirm by remember { mutableStateOf(false) }
    var resultDismissed by remember { mutableStateOf(false) }

    // Read version so Compose recomposes after each move.
    @Suppress("UNUSED_EXPRESSION") version

    val finished = game.finished
    val status = when {
        finished && game.resignedBy != null ->
            "对局结束：${if (game.resignedBy == Stone.BLACK) "黑" else "白"}方认输"
        finished -> "对局结束"
        else -> "第 ${game.moves.size + 1} 手 · ${if (game.current == Stone.BLACK) "黑方" else "白方"}落子"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = status, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onExit) { Text("返回") }
        }
        Text(
            text = "黑提子 ${game.capturesByBlack} · 白提子 ${game.capturesByWhite}" +
                if (config.handicap > 0) " · 让 ${config.handicap} 子" else "",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))
        BoardView(game = game, enabled = !finished) { x, y ->
            if (game.play(x, y)) {
                version++
                resultDismissed = false
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (game.pass()) { version++; resultDismissed = false } },
                enabled = !finished,
                modifier = Modifier.weight(1f)
            ) { Text("停一手") }
            OutlinedButton(
                onClick = { if (game.undo()) version++ },
                enabled = game.moves.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("悔棋") }
            OutlinedButton(
                onClick = { showResignConfirm = true },
                enabled = !finished,
                modifier = Modifier.weight(1f)
            ) { Text("认输") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val sgf = Sgf.export(game)
                val dir = File(context.filesDir, "sgf").apply { mkdirs() }
                val file = File(dir, "tuxungo-${System.currentTimeMillis()}.sgf")
                file.writeText(sgf)
                Toast.makeText(context, "棋谱已保存：${file.name}", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("导出 SGF 棋谱") }
    }

    if (showResignConfirm) {
        AlertDialog(
            onDismissRequest = { showResignConfirm = false },
            title = { Text("确认认输？") },
            text = { Text("${if (game.current == Stone.BLACK) "黑方" else "白方"}将判负。") },
            confirmButton = {
                TextButton(onClick = {
                    game.resign()
                    showResignConfirm = false
                    version++
                    resultDismissed = false
                }) { Text("认输") }
            },
            dismissButton = {
                TextButton(onClick = { showResignConfirm = false }) { Text("取消") }
            }
        )
    }

    if (finished && !resultDismissed) {
        val resultText = if (game.resignedBy != null) {
            "${if (game.resignedBy == Stone.BLACK) "白" else "黑"}方胜（对方认输）"
        } else {
            val s = game.score()
            val winner = if (s.lead > 0) "黑" else "白"
            val diff = kotlin.math.abs(s.lead)
            val diffText = if (diff % 1.0 == 0.0) diff.toInt().toString() else diff.toString()
            "${winner}方胜 $diffText 目\n" +
                "黑：棋子 ${s.blackStones} + 空 ${s.blackTerritory} = ${s.blackTotal.toInt()}\n" +
                "白：棋子 ${s.whiteStones} + 空 ${s.whiteTerritory} + 贴目 ${s.komi} = ${s.whiteTotal}"
        }
        AlertDialog(
            onDismissRequest = { resultDismissed = true },
            title = { Text("对局结果") },
            text = { Text(resultText) },
            confirmButton = {
                TextButton(onClick = onExit) { Text("返回主页") }
            },
            dismissButton = {
                TextButton(onClick = { resultDismissed = true }) { Text("查看棋盘") }
            }
        )
    }
}
