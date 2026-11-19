package com.tuxungo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import kotlin.math.roundToInt

/** A numbered candidate-move marker (rank 1 is the AI's first choice). */
data class CandidateMark(val x: Int, val y: Int, val rank: Int)

private val BoardLight = Color(0xFFE3BC7C)
private val BoardDeep = Color(0xFFC99B52)
private val BoardEdge = Color(0xFF9A6B2F)
private val GridInk = Color(0xFF4A2E14)
private val CoordInk = Color(0xFF7A5C33)
private val MarkerBlue = Color(0xFF2456C8)

private const val COLUMN_LETTERS = "ABCDEFGHJKLMNOPQRSTUVWXYZ"

/**
 * The board: wooden face with grain, coordinates, shaded slate-and-shell
 * style stones with drop shadows, territory shading, candidate markers,
 * variation ghosts and a translucent preview stone for tap-to-confirm.
 */
@Composable
fun BoardView(
    game: GoGame,
    enabled: Boolean,
    candidates: List<CandidateMark> = emptyList(),
    ownership: DoubleArray? = null,
    pvGhost: List<Move> = emptyList(),
    preview: Pair<Int, Int>? = null,
    onPointTap: (Int, Int) -> Unit
) {
    val w = game.size.width
    val h = game.size.height
    // Deterministic wood grain so the texture never flickers on recompose.
    val grain = remember(w, h) {
        val rnd = kotlin.random.Random(20261119)
        List(30) {
            Triple(rnd.nextFloat(), rnd.nextFloat() * 0.5f + 0.2f, rnd.nextFloat())
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(game, enabled) {
                detectTapGestures { offset ->
                    if (!enabled) return@detectTapGestures
                    val label = size.width * 0.052f
                    val cellX = (size.width - 2 * label) / w
                    val cellY = (size.height - 2 * label) / h
                    val originX = label + cellX / 2f
                    val originY = label + cellY / 2f
                    val fx = (offset.x - originX) / cellX
                    val fy = (offset.y - originY) / cellY
                    val ix = fx.roundToInt()
                    val iy = fy.roundToInt()
                    // Snap to the nearest intersection within half a cell:
                    // forgiving for fingers, no dead zones between points.
                    if (ix in 0 until w && iy in 0 until h &&
                        kotlin.math.abs(fx - ix) <= 0.5f && kotlin.math.abs(fy - iy) <= 0.5f
                    ) {
                        onPointTap(ix, iy)
                    }
                }
            }
    ) {
        val s = size.width
        val label = s * 0.052f
        val cellX = (s - 2 * label) / w
        val cellY = (s - 2 * label) / h
        val originX = label + cellX / 2f
        val originY = label + cellY / 2f
        val r = minOf(cellX, cellY) * 0.475f
        fun px(x: Int) = originX + x * cellX
        fun py(y: Int) = originY + y * cellY

        val faceLeft = originX - cellX * 0.64f
        val faceTop = originY - cellY * 0.64f
        val faceSize = Size(px(w - 1) - faceLeft + cellX * 0.64f, py(h - 1) - faceTop + cellY * 0.64f)
        val corner = CornerRadius(s * 0.014f, s * 0.014f)

        // Soft shadow under the board, then the wooden edge and face.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(faceLeft + s * 0.006f, faceTop + s * 0.014f),
            size = faceSize, cornerRadius = corner
        )
        drawRoundRect(color = BoardEdge, topLeft = Offset(faceLeft, faceTop), size = faceSize, cornerRadius = corner)
        val faceInset = s * 0.006f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(BoardLight, BoardDeep),
                start = Offset(faceLeft, faceTop),
                end = Offset(faceLeft + faceSize.width, faceTop + faceSize.height)
            ),
            topLeft = Offset(faceLeft + faceInset, faceTop + faceInset),
            size = Size(faceSize.width - faceInset * 2, faceSize.height - faceInset * 2),
            cornerRadius = corner
        )
        // Wood grain streaks, clipped to the face.
        clipRect(
            left = faceLeft + faceInset, top = faceTop + faceInset,
            right = faceLeft + faceSize.width - faceInset, bottom = faceTop + faceSize.height - faceInset
        ) {
            for ((fy, lenFrac, alphaSeed) in grain) {
                val gy = faceTop + fy * faceSize.height
                val gx = faceLeft + (alphaSeed - 0.5f) * faceSize.width * 0.3f
                drawRect(
                    color = Color(0xFF8A5A24).copy(alpha = 0.028f + alphaSeed * 0.035f),
                    topLeft = Offset(gx, gy),
                    size = Size(faceSize.width * lenFrac * 1.6f, maxOf(1.1f, s * 0.0022f))
                )
            }
        }

        // Grid lines and border.
        val lineW = maxOf(1.1f, s * 0.0016f)
        for (x in 0 until w) {
            drawLine(GridInk.copy(alpha = 0.88f), Offset(px(x), py(0)), Offset(px(x), py(h - 1)), strokeWidth = lineW)
        }
        for (y in 0 until h) {
            drawLine(GridInk.copy(alpha = 0.88f), Offset(px(0), py(y)), Offset(px(w - 1), py(y)), strokeWidth = lineW)
        }
        drawRect(
            color = GridInk,
            topLeft = Offset(px(0), py(0)),
            size = Size(px(w - 1) - px(0), py(h - 1) - py(0)),
            style = Stroke(width = maxOf(1.8f, s * 0.0032f))
        )

        // Star points for square boards with a standard layout.
        val star = starPoints(w, h)
        for ((x, y) in star) {
            drawCircle(GridInk, radius = maxOf(2.4f, r * 0.15f), center = Offset(px(x), py(y)))
        }

        // Coordinates: letters skip I, numbers count down from the top.
        val coordPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(122, 92, 51)
            textSize = label * 0.58f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val coordMetrics = coordPaint.fontMetrics
        for (x in 0 until w) {
            val letter = COLUMN_LETTERS[x].toString()
            val cx = px(x)
            drawContext.canvas.nativeCanvas.drawText(
                letter, cx, faceTop - label * 0.20f - (coordMetrics.ascent + coordMetrics.descent) / 2f, coordPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                letter, cx, faceTop + faceSize.height + label * 0.20f - (coordMetrics.ascent + coordMetrics.descent) / 2f, coordPaint
            )
        }
        for (y in 0 until h) {
            val num = (h - y).toString()
            val cy = py(y) - (coordMetrics.ascent + coordMetrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText(num, faceLeft - label * 0.42f, cy, coordPaint)
            drawContext.canvas.nativeCanvas.drawText(num, faceLeft + faceSize.width + label * 0.42f, cy, coordPaint)
        }

        // Territory shading from analysis ownership (Black's perspective).
        if (ownership != null) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (game.stoneAt(x, y) != null) continue
                    val v = ownership.getOrNull(y * w + x) ?: continue
                    if (kotlin.math.abs(v) <= 0.15) continue
                    val alpha = (0.10f + 0.33f * minOf(1.0, kotlin.math.abs(v)).toFloat())
                    val half = minOf(cellX, cellY) * 0.33f
                    drawRoundRect(
                        color = if (v > 0) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha),
                        topLeft = Offset(px(x) - half, py(y) - half),
                        size = Size(half * 2, half * 2),
                        cornerRadius = CornerRadius(half * 0.3f, half * 0.3f)
                    )
                }
            }
        }

        // Stones with shadows and radial shading.
        fun drawStone(cx: Float, cy: Float, stone: Stone, alpha: Float) {
            drawCircle(
                Color.Black.copy(alpha = 0.26f * alpha),
                radius = r * 0.97f,
                center = Offset(cx + r * 0.10f, cy + r * 0.17f)
            )
            val brush = if (stone == Stone.BLACK) {
                Brush.radialGradient(
                    colors = listOf(Color(0xFF777777), Color(0xFF2B2B2B), Color(0xFF060606)),
                    center = Offset(cx - r * 0.36f, cy - r * 0.42f),
                    radius = r * 1.3f
                )
            } else {
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF2EEE3), Color(0xFFCFC7B4)),
                    center = Offset(cx - r * 0.32f, cy - r * 0.40f),
                    radius = r * 1.3f
                )
            }
            drawCircle(brush = brush, radius = r, center = Offset(cx, cy), alpha = alpha)
            if (stone == Stone.WHITE) {
                drawCircle(
                    Color(0xFFA79E8B).copy(alpha = 0.55f * alpha),
                    radius = r, center = Offset(cx, cy),
                    style = Stroke(width = maxOf(1f, r * 0.045f))
                )
            } else {
                // Specular highlight on black stones.
                drawCircle(
                    Color.White.copy(alpha = 0.30f * alpha),
                    radius = r * 0.30f,
                    center = Offset(cx - r * 0.36f, cy - r * 0.44f)
                )
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val stone = game.stoneAt(x, y) ?: continue
                drawStone(px(x), py(y), stone, 1f)
            }
        }

        // Last-move marker: a vermillion ring.
        val last = game.lastMove()
        if (last != null && game.stoneAt(last.x, last.y) == last.stone) {
            drawCircle(
                Color(0xFFD84315),
                radius = r * 0.52f,
                center = Offset(px(last.x), py(last.y)),
                style = Stroke(width = maxOf(2f, r * 0.11f))
            )
        }

        // Preview stone for tap-to-confirm placement.
        if (preview != null) {
            val (pvx, pvy) = preview
            if (game.stoneAt(pvx, pvy) == null) {
                drawStone(px(pvx), py(pvy), game.current, 0.55f)
                drawCircle(
                    MarkerBlue,
                    radius = r * 1.06f,
                    center = Offset(px(pvx), py(pvy)),
                    style = Stroke(width = maxOf(2.2f, r * 0.09f))
                )
            }
        }

        // Principal-variation ghost stones (semi-transparent, numbered).
        pvGhost.forEachIndexed { i, mv ->
            if (mv.isPass) return@forEachIndexed
            val center = Offset(px(mv.x), py(mv.y))
            drawCircle(
                if (mv.stone == Stone.BLACK) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.6f),
                radius = r * 0.86f,
                center = center
            )
            val paint = android.graphics.Paint().apply {
                color = if (mv.stone == Stone.BLACK) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                textSize = r * 0.72f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            val metrics = paint.fontMetrics
            val baseline = center.y - (metrics.ascent + metrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText("${i + 1}", center.x, baseline, paint)
        }
        // Candidate markers: rank 1 is a solid blue disc with a white
        // number; other ranks are pale discs with a blue ring and number.
        for (mark in candidates) {
            val center = Offset(px(mark.x), py(mark.y))
            if (mark.rank == 1) {
                drawCircle(MarkerBlue, radius = r * 0.66f, center = center)
            } else {
                drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.58f, center = center)
                drawCircle(MarkerBlue, radius = r * 0.58f, center = center, style = Stroke(width = 2.5f))
            }
            val paint = android.graphics.Paint().apply {
                color = if (mark.rank == 1) android.graphics.Color.WHITE else android.graphics.Color.rgb(36, 86, 200)
                textSize = r * 0.78f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            val metrics = paint.fontMetrics
            val baseline = center.y - (metrics.ascent + metrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText("${mark.rank}", center.x, baseline, paint)
        }
    }
}

private fun starPoints(w: Int, h: Int): List<Pair<Int, Int>> {
    if (w != h) return emptyList()
    val coords: Triple<Int, Int, Int> = when (w) {
        19 -> Triple(3, 9, 15)
        13 -> Triple(3, 6, 9)
        9 -> Triple(2, 4, 6)
        else -> return emptyList()
    }
    val (a, b, c) = coords
    return listOf(a to a, c to a, a to c, c to c, b to b, a to b, c to b, b to a, b to c)
}
