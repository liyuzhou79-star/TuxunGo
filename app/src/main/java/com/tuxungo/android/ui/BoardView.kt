package com.tuxungo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Stone
import kotlin.math.roundToInt

private val BoardWood = Color(0xFFE8C07A)
private val GridLine = Color(0xFF8A6D3B)
private val MarkerBlue = Color(0xFF2F7CF6)
private val LastMoveRed = Color(0xFFD43D2A)

@Composable
fun BoardView(
    game: GoGame,
    enabled: Boolean,
    suggestion: Pair<Int, Int>? = null,
    onPointTap: (Int, Int) -> Unit
) {
    val w = game.size.width
    val h = game.size.height
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(w.toFloat() / h.toFloat())
            .pointerInput(game, enabled) {
                detectTapGestures { offset ->
                    if (!enabled) return@detectTapGestures
                    val cellX = size.width / (w + 1f)
                    val cellY = size.height / (h + 1f)
                    // Snap to the nearest intersection (clamped to the
                    // board) instead of dropping taps that land in the
                    // margin or slightly off a line; accept anything
                    // within half a cell of that intersection.
                    val gx = (offset.x / cellX - 1).roundToInt().coerceIn(0, w - 1)
                    val gy = (offset.y / cellY - 1).roundToInt().coerceIn(0, h - 1)
                    val px = cellX * (gx + 1)
                    val py = cellY * (gy + 1)
                    val dx = offset.x - px
                    val dy = offset.y - py
                    val radius = minOf(cellX, cellY) * 0.5f
                    if (dx * dx + dy * dy <= radius * radius) {
                        onPointTap(gx, gy)
                    }
                }
            }
    ) {
        val cellX = size.width / (w + 1f)
        val cellY = size.height / (h + 1f)
        drawRect(color = BoardWood)
        // Grid
        for (i in 0 until w) {
            val x = cellX * (i + 1)
            drawLine(GridLine, Offset(x, cellY), Offset(x, size.height - cellY), strokeWidth = 2f)
        }
        for (j in 0 until h) {
            val y = cellY * (j + 1)
            drawLine(GridLine, Offset(cellX, y), Offset(size.width - cellX, y), strokeWidth = 2f)
        }
        // Star points on standard square boards
        for ((sx, sy) in starPoints(w, h)) {
            drawCircle(
                GridLine,
                radius = cellX * 0.09f,
                center = Offset(cellX * (sx + 1), cellY * (sy + 1))
            )
        }
        // Stones
        val last = game.lastMove()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val stone = game.stoneAt(x, y) ?: continue
                val center = Offset(cellX * (x + 1), cellY * (y + 1))
                drawCircle(
                    Color(0x33000000),
                    radius = cellX * 0.46f,
                    center = center + Offset(2f, 3f)
                )
                drawCircle(
                    if (stone == Stone.BLACK) Color(0xFF1A1A1A) else Color(0xFFF7F7F7),
                    radius = cellX * 0.46f,
                    center = center
                )
                if (stone == Stone.WHITE) {
                    drawCircle(
                        Color(0xFFBBBBBB),
                        radius = cellX * 0.46f,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                }
                if (last != null && last.x == x && last.y == y) {
                    drawCircle(
                        LastMoveRed,
                        radius = cellX * 0.16f,
                        center = center,
                        style = Stroke(width = 3.5f)
                    )
                }
            }
        }
        // AI first-choice suggestion: solid blue disc with a white "1".
        if (suggestion != null) {
            val (sx, sy) = suggestion
            val center = Offset(cellX * (sx + 1), cellY * (sy + 1))
            drawCircle(MarkerBlue, radius = cellX * 0.34f, center = center)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = cellX * 0.42f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            val metrics = paint.fontMetrics
            val baseline = center.y - (metrics.ascent + metrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText("1", center.x, baseline, paint)
        }
    }
}

private fun starPoints(w: Int, h: Int): List<Pair<Int, Int>> {
    if (w != h) return emptyList()
    val pts: List<Int> = when (w) {
        19 -> listOf(3, 9, 15)
        13 -> listOf(3, 6, 9)
        9 -> listOf(2, 4, 6)
        else -> return emptyList()
    }
    return pts.flatMap { x -> pts.map { y -> x to y } }
}
