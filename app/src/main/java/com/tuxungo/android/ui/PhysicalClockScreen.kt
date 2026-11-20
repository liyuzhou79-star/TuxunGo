package com.tuxungo.android.ui

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tuxungo.android.GameStore
import com.tuxungo.android.SettingsStore
import com.tuxungo.android.go.Stone
import kotlinx.coroutines.delay

/**
 * A clock for games played on a real board: the phone is the
 * clock. Tap your own side when you finish a move. Same timing
 * rules as the in-app clock (main time, then byoyomi 3 or 5
 * periods of 30 seconds; a move in byoyomi refills the period).
 * No engine involved — pure UI and timing.
 */
@Composable
fun PhysicalClockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.load(context) }

    // ---- Sounds + haptics (same stone-click sound as the game) ----
    val soundPool = remember {
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
    val placeSoundIds = remember {
        SettingsStore.placeSoundResList(settings.placeSound)
            .map { soundPool.load(context, it, 1) }
    }
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE)
                as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                as android.os.Vibrator
        }
    }
    DisposableEffect(Unit) {
        onDispose { soundPool.release() }
    }

    fun beep() {
        if (!settings.soundEnabled || placeSoundIds.isEmpty()) return
        soundPool.play(placeSoundIds.random(), 0.9f, 0.9f, 1, 0, 1f)
    }

    fun buzz() {
        if (!settings.vibrationEnabled) return
        try {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    60, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (_: Exception) {
        }
    }

    // ---- Clock state ----
    var minutes by remember { mutableIntStateOf(10) }
    var periods by remember { mutableIntStateOf(3) }
    var clock by remember { mutableStateOf<GameStore.ClockSnapshot?>(null) }
    var active by remember { mutableStateOf<Stone?>(null) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var winner by remember { mutableStateOf<Stone?>(null) }
    var showWinnerDialog by remember { mutableStateOf(false) }
    var warnedBlack by remember { mutableStateOf(false) }
    var warnedWhite by remember { mutableStateOf(false) }

    fun startClock() {
        clock = GameStore.ClockSnapshot(
            blackMainMs = minutes * 60_000L,
            whiteMainMs = minutes * 60_000L,
            blackPeriods = periods,
            whitePeriods = periods,
            blackPeriodMs = 30_000L,
            whitePeriodMs = 30_000L
        )
        active = Stone.BLACK
        running = true
        paused = false
        winner = null
        showWinnerDialog = false
        warnedBlack = false
        warnedWhite = false
    }

    fun resetClock() {
        clock = null
        active = null
        running = false
        paused = false
        winner = null
        showWinnerDialog = false
    }

    /** Press your own clock after moving: refills your byoyomi
     * period if you are in it, then the other side starts. */
    fun pressClock(stone: Stone) {
        val c = clock ?: return
        if (!running || paused || winner != null || active != stone) return
        clock = when (stone) {
            Stone.BLACK -> if (c.blackMainMs <= 0) {
                c.copy(blackPeriodMs = 30_000L)
            } else {
                c
            }
            Stone.WHITE -> if (c.whiteMainMs <= 0) {
                c.copy(whitePeriodMs = 30_000L)
            } else {
                c
            }
        }
        active = stone.other()
        beep()
        buzz()
    }

    // ---- Tick: same drain rules as the in-app game clock ----
    LaunchedEffect(running, paused) {
        if (!running) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (true) {
            delay(200)
            val now = System.currentTimeMillis()
            val dt = now - last
            last = now
            val c = clock ?: continue
            if (paused || winner != null) continue
            when (active) {
                Stone.BLACK -> {
                    when {
                        c.blackMainMs > 0 -> {
                            val left = (c.blackMainMs - dt).coerceAtLeast(0)
                            if (left == 0L && !warnedBlack) {
                                warnedBlack = true
                                beep()
                                buzz()
                            }
                            clock = c.copy(blackMainMs = left)
                        }
                        c.blackPeriodMs > dt -> {
                            clock = c.copy(blackPeriodMs = c.blackPeriodMs - dt)
                        }
                        c.blackPeriods > 1 -> {
                            clock = c.copy(
                                blackPeriods = c.blackPeriods - 1,
                                blackPeriodMs = 30_000L
                            )
                            beep()
                            buzz()
                        }
                        else -> {
                            clock = c.copy(blackPeriods = 0, blackPeriodMs = 0)
                            winner = Stone.WHITE
                            showWinnerDialog = true
                            running = false
                            beep()
                            buzz()
                        }
                    }
                }
                Stone.WHITE -> {
                    when {
                        c.whiteMainMs > 0 -> {
                            val left = (c.whiteMainMs - dt).coerceAtLeast(0)
                            if (left == 0L && !warnedWhite) {
                                warnedWhite = true
                                beep()
                                buzz()
                            }
                            clock = c.copy(whiteMainMs = left)
                        }
                        c.whitePeriodMs > dt -> {
                            clock = c.copy(whitePeriodMs = c.whitePeriodMs - dt)
                        }
                        c.whitePeriods > 1 -> {
                            clock = c.copy(
                                whitePeriods = c.whitePeriods - 1,
                                whitePeriodMs = 30_000L
                            )
                            beep()
                            buzz()
                        }
                        else -> {
                            clock = c.copy(whitePeriods = 0, whitePeriodMs = 0)
                            winner = Stone.BLACK
                            showWinnerDialog = true
                            running = false
                            beep()
                            buzz()
                        }
                    }
                }
                null -> {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "实体棋盘棋钟",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text(
            text = "两个人用真棋盘下棋，手机当棋钟：下完一步，点自己这边的钟。",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))

        val c = clock
        if (c == null) {
            Text(text = "主时间", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(5, 10, 20, 30, 60)) {
                    FilterChip(
                        selected = minutes == m,
                        onClick = { minutes = m },
                        label = { Text("$m 分钟") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "读秒次数（每次 30 秒）", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = periods == 3,
                    onClick = { periods = 3 },
                    label = { Text("3 次") }
                )
                FilterChip(
                    selected = periods == 5,
                    onClick = { periods = 5 },
                    label = { Text("5 次") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "主时间用完进入读秒：30 秒内落子可保住当期读秒，读秒全部用完判负。",
                fontSize = 12.sp, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { startClock() },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("开始计时（黑方先行）", fontSize = 17.sp) }
        } else {
            ClockPanel(
                isBlack = true,
                mainMs = c.blackMainMs,
                periodsLeft = c.blackPeriods,
                periodMs = c.blackPeriodMs,
                isActive = active == Stone.BLACK && running && !paused && winner == null,
                hint = when {
                    winner != null -> "对局结束"
                    paused -> "已暂停"
                    active == Stone.BLACK -> "轮到你 · 点这里按钟"
                    else -> "对方行棋中"
                },
                onPress = { pressClock(Stone.BLACK) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ClockPanel(
                isBlack = false,
                mainMs = c.whiteMainMs,
                periodsLeft = c.whitePeriods,
                periodMs = c.whitePeriodMs,
                isActive = active == Stone.WHITE && running && !paused && winner == null,
                hint = when {
                    winner != null -> "对局结束"
                    paused -> "已暂停"
                    active == Stone.WHITE -> "轮到你 · 点这里按钟"
                    else -> "对方行棋中"
                },
                onPress = { pressClock(Stone.WHITE) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (winner != null) {
                Text(
                    text = if (winner == Stone.BLACK) "黑方超时 · 白方胜" else "白方超时 · 黑方胜",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "提示：下完一步点自己这边的钟；进入读秒或读秒少一次时，会响一声、震一下。",
                fontSize = 12.sp, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { paused = !paused },
                    enabled = winner == null,
                    modifier = Modifier.weight(1f)
                ) { Text(if (paused) "继续" else "暂停") }
                OutlinedButton(
                    onClick = { resetClock() },
                    modifier = Modifier.weight(1f)
                ) { Text("重置") }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (winner != null && showWinnerDialog) {
        AlertDialog(
            onDismissRequest = { showWinnerDialog = false },
            title = { Text("对局结束") },
            text = {
                Text(
                    if (winner == Stone.BLACK) "白方超时，黑方胜。" else "黑方超时，白方胜。"
                )
            },
            confirmButton = {
                TextButton(onClick = { startClock() }) { Text("再来一局") }
            },
            dismissButton = {
                TextButton(onClick = { showWinnerDialog = false }) { Text("查看棋盘") }
            }
        )
    }
}

@Composable
private fun ClockPanel(
    isBlack: Boolean,
    mainMs: Long,
    periodsLeft: Int,
    periodMs: Long,
    isActive: Boolean,
    hint: String,
    onPress: () -> Unit
) {
    val bg = if (isBlack) Color(0xFF263238) else Color(0xFFFAFAFA)
    val fg = if (isBlack) Color.White else Color(0xFF212121)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(
            if (isActive) 2.dp else 1.dp,
            if (isActive) Color(0xFFD64F7C) else Color.LightGray
        ),
        onClick = onPress
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isBlack) "黑方" else "白方",
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (mainMs > 0) {
                val s = (mainMs + 999) / 1000
                Text(
                    text = "%d:%02d".format(s / 60, s % 60),
                    fontSize = 52.sp, fontWeight = FontWeight.Bold, color = fg
                )
                Text(
                    text = "主时间 · 读秒 $periodsLeft 次",
                    fontSize = 12.sp, color = fg.copy(alpha = 0.7f)
                )
            } else if (periodsLeft > 0) {
                Text(
                    text = "${(periodMs + 999) / 1000} 秒",
                    fontSize = 52.sp, fontWeight = FontWeight.Bold, color = fg
                )
                Text(
                    text = "读秒 · 还剩 $periodsLeft 次",
                    fontSize = 12.sp, color = fg.copy(alpha = 0.7f)
                )
            } else {
                Text(
                    text = "时间到",
                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = fg
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hint,
                fontSize = 13.sp,
                color = if (isActive) Color(0xFFD64F7C) else fg.copy(alpha = 0.6f)
            )
        }
    }
}
