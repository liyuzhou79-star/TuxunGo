package com.tuxungo.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Move
import com.tuxungo.android.go.Stone
import kotlin.math.roundToInt

/** A numbered candidate-move marker (rank 1 is the AI's first choice). */
data class CandidateMark(val x: Int, val y: Int, val rank: Int)

/** Board face designs, selectable in settings. */
enum class BoardSkin {
    WOOD_LIGHT, WOOD_DARK, BAMBOO, SAKURA;

    companion object {
        fun fromId(id: String): BoardSkin = when (id) {
            "wood_dark" -> WOOD_DARK
            "bamboo" -> BAMBOO
            "sakura" -> SAKURA
            else -> WOOD_LIGHT
        }
    }
}

private val GridInk = Color(0xFF4A2E14)
private val GridInkOnDark = Color(0xFFF0DCAE)
private val CoordInk = Color(0xFF7A5C33)
private val CoordInkOnDark = Color(0xFFD9BE8C)
private val AccentRose = Color(0xFFD81B60)

private const val COLUMN_LETTERS = "ABCDEFGHJKLMNOPQRSTUVWXYZ"

/**
 * The board: real wood textures or flat designs, coordinates, shaded
 * stones with drop shadows, territory squares, dead-stone squares,
 * candidate markers, variation ghosts and a preview stone for
 * tap-to-confirm placement.
 */
@Composable
fun BoardView(
    game: GoGame,
    enabled: Boolean,
    skin: BoardSkin = BoardSkin.WOOD_LIGHT,
    showCoordinates: Boolean = true,
    territoryThreshold: Double = 0.65,
    candidates: List<CandidateMark> = emptyList(),
    ownership: DoubleArray? = null,
    pvGhost: List<Move> = emptyList(),
    preview: Pair<Int, Int>? = null,
    /** Stones marked dead in scoring mode: drawn faded with a cross. */
    markedDead: Set<Int> = emptySet(),
    onPointTap: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val w = game.size.width
    val h = game.size.height
    // Deterministic wood grain for the flat designs.
    val grain = remember(w, h) {
        val rnd = kotlin.random.Random(20261119)
        List(30) {
            Triple(rnd.nextFloat(), rnd.nextFloat() * 0.5f + 0.2f, rnd.nextFloat())
        }
    }
    val texture: ImageBitmap? = remember(skin) {
        val asset = when (skin) {
            BoardSkin.WOOD_LIGHT -> "board/wood_light.jpg"
            BoardSkin.WOOD_DARK -> "board/wood_dark.jpg"
            else -> null
        }
        asset?.let {
            try {
                context.assets.open(it).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    val darkSkin = skin == BoardSkin.WOOD_DARK
    val ink = if (darkSkin) GridInkOnDark else GridInk
    val coordInk = if (darkSkin) CoordInkOnDark else CoordInk
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(game, enabled, showCoordinates) {
                detectTapGestures { offset ->
                    if (!enabled) return@detectTapGestures
                    val label = size.width * if (showCoordinates) 0.052f else 0.018f
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
        val label = s * if (showCoordinates) 0.052f else 0.018f
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
        val edgeColor = when (skin) {
            BoardSkin.WOOD_LIGHT -> Color(0xFFB0823F)
            BoardSkin.WOOD_DARK -> Color(0xFF2E1B0E)
            BoardSkin.BAMBOO -> Color(0xFF9A6B2F)
            BoardSkin.SAKURA -> Color(0xFFD98CA6)
        }

        // Soft shadow under the board, then the edge and the face.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(faceLeft + s * 0.006f, faceTop + s * 0.014f),
            size = faceSize, cornerRadius = corner
        )
        drawRoundRect(color = edgeColor, topLeft = Offset(faceLeft, faceTop), size = faceSize, cornerRadius = corner)
        val faceInset = s * 0.006f
        val innerLeft = faceLeft + faceInset
        val innerTop = faceTop + faceInset
        val innerSize = Size(faceSize.width - faceInset * 2, faceSize.height - faceInset * 2)
        if (texture != null) {
            val facePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        innerLeft, innerTop,
                        innerLeft + innerSize.width, innerTop + innerSize.height,
                        corner
                    )
                )
            }
            clipPath(facePath) {
                drawImage(
                    image = texture,
                    dstOffset = IntOffset(innerLeft.toInt(), innerTop.toInt()),
                    dstSize = IntSize(innerSize.width.toInt(), innerSize.height.toInt())
                )
            }
        } else {
            val (light, deep) = when (skin) {
                BoardSkin.SAKURA -> Color(0xFFFCE9F1) to Color(0xFFF7CFDE)
                else -> Color(0xFFEECC95) to Color(0xFFDDB26E)
            }
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(light, deep),
                    start = Offset(faceLeft, faceTop),
                    end = Offset(faceLeft + faceSize.width, faceTop + faceSize.height)
                ),
                topLeft = Offset(innerLeft, innerTop),
                size = innerSize,
                cornerRadius = corner
            )
            clipRect(
                left = innerLeft, top = innerTop,
                right = innerLeft + innerSize.width, bottom = innerTop + innerSize.height
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
        }

        // Grid lines and border.
        val lineW = maxOf(1.1f, s * 0.0016f)
        for (x in 0 until w) {
            drawLine(ink.copy(alpha = 0.88f), Offset(px(x), py(0)), Offset(px(x), py(h - 1)), strokeWidth = lineW)
        }
        for (y in 0 until h) {
            drawLine(ink.copy(alpha = 0.88f), Offset(px(0), py(y)), Offset(px(w - 1), py(y)), strokeWidth = lineW)
        }
        drawRect(
            color = ink,
            topLeft = Offset(px(0), py(0)),
            size = Size(px(w - 1) - px(0), py(h - 1) - py(0)),
            style = Stroke(width = maxOf(1.8f, s * 0.0032f))
        )

        // Star points for square boards with a standard layout.
        val star = starPoints(w, h)
        for ((x, y) in star) {
            drawCircle(ink, radius = maxOf(2.4f, r * 0.15f), center = Offset(px(x), py(y)))
        }

        // Coordinates: letters skip I, numbers count down from the top.
        if (showCoordinates) {
            val coordPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    255,
                    (coordInk.red * 255).toInt(),
                    (coordInk.green * 255).toInt(),
                    (coordInk.blue * 255).toInt()
                )
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
        }

        // Territory marks: small solid squares in the owner's color on
        // empty points, only where the engine is fairly confident
        // (threshold), so early-game influence is not shown as territory.
        if (ownership != null) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (game.stoneAt(x, y) != null) continue
                    val v = ownership.getOrNull(y * w + x) ?: continue
                    val mag = kotlin.math.abs(v)
                    if (mag < territoryThreshold) continue
                    val alpha = (0.55f + 0.45f * ((mag - territoryThreshold) / (1.0 - territoryThreshold)).coerceIn(0.0, 1.0).toFloat())
                    val half = minOf(cellX, cellY) * 0.19f
                    val topLeft = Offset(px(x) - half, py(y) - half)
                    val sqSize = Size(half * 2, half * 2)
                    if (v > 0) {
                        drawRect(color = Color.Black.copy(alpha = alpha), topLeft = topLeft, size = sqSize)
                    } else {
                        drawRect(color = Color.White.copy(alpha = alpha), topLeft = topLeft, size = sqSize)
                        drawRect(
                            color = Color(0xFF8A7A5C).copy(alpha = alpha),
                            topLeft = topLeft, size = sqSize,
                            style = Stroke(width = maxOf(1f, r * 0.04f))
                        )
                    }
                }
            }
        }

        // Stones with shadows and gentle radial shading.
        fun drawStone(cx: Float, cy: Float, stone: Stone, alpha: Float) {
            drawCircle(
                Color.Black.copy(alpha = 0.26f * alpha),
                radius = r * 0.97f,
                center = Offset(cx + r * 0.10f, cy + r * 0.17f)
            )
            val brush = if (stone == Stone.BLACK) {
                Brush.radialGradient(
                    colors = listOf(Color(0xFF4D4D4D), Color(0xFF161616), Color(0xFF000000)),
                    center = Offset(cx - r * 0.30f, cy - r * 0.36f),
                    radius = r * 1.25f
                )
            } else {
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF5F5F5), Color(0xFFD9D9D9)),
                    center = Offset(cx - r * 0.28f, cy - r * 0.34f),
                    radius = r * 1.25f
                )
            }
            drawCircle(brush = brush, radius = r, center = Offset(cx, cy), alpha = alpha)
            if (stone == Stone.WHITE) {
                drawCircle(
                    Color(0xFF9A9A9A).copy(alpha = 0.6f * alpha),
                    radius = r, center = Offset(cx, cy),
                    style = Stroke(width = maxOf(1f, r * 0.045f))
                )
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val stone = game.stoneAt(x, y) ?: continue
                val dead = game.index(x, y) in markedDead
                drawStone(px(x), py(y), stone, if (dead) 0.45f else 1f)
                if (dead) {
                    // Red cross over scoring-mode dead stones.
                    val cx = px(x)
                    val cy = py(y)
                    val d = r * 0.52f
                    drawLine(Color(0xFFC62828), Offset(cx - d, cy - d), Offset(cx + d, cy + d), strokeWidth = maxOf(2f, r * 0.10f))
                    drawLine(Color(0xFFC62828), Offset(cx - d, cy + d), Offset(cx + d, cy - d), strokeWidth = maxOf(2f, r * 0.10f))
                }
            }
        }

        // Dead stones: a stone whose point the engine judges to belong
        // to the opponent gets a small square in the opponent's color.
        if (ownership != null) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val stone = game.stoneAt(x, y) ?: continue
                    val v = ownership.getOrNull(y * w + x) ?: continue
                    val dead = (stone == Stone.WHITE && v > territoryThreshold) ||
                        (stone == Stone.BLACK && v < -territoryThreshold)
                    if (!dead) continue
                    val half = r * 0.34f
                    val topLeft = Offset(px(x) - half, py(y) - half)
                    val sqSize = Size(half * 2, half * 2)
                    if (stone == Stone.WHITE) {
                        drawRect(color = Color.Black, topLeft = topLeft, size = sqSize)
                    } else {
                        drawRect(color = Color.White, topLeft = topLeft, size = sqSize)
                    }
                }
            }
        }

        // Last-move marker, Tencent style: a right triangle at the
        // stone's center whose two legs lie exactly along the grid
        // lines running under the stone (right and down).
        val last = game.lastMove()
        if (last != null && game.stoneAt(last.x, last.y) == last.stone) {
            val cx = px(last.x)
            val cy = py(last.y)
            val tri = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + r * 0.74f, cy)
                lineTo(cx, cy + r * 0.74f)
                close()
            }
            drawPath(
                tri,
                color = if (last.stone == Stone.BLACK) Color.White.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.88f)
            )
        }

        // Preview stone for tap-to-confirm placement.
        if (preview != null) {
            val (pvx, pvy) = preview
            if (game.stoneAt(pvx, pvy) == null) {
                drawStone(px(pvx), py(pvy), game.current, 0.55f)
                drawCircle(
                    AccentRose,
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
        // Candidate markers: large, high-contrast discs with a drop
        // shadow and a pale halo. Rank 1 (the first choice) is a big
        // rose disc; other ranks are white discs with a bold dark ring
        // and number.
        for (mark in candidates) {
            val center = Offset(px(mark.x), py(mark.y))
            val discR = if (mark.rank == 1) r * 0.80f else r * 0.70f
            drawCircle(
                Color.Black.copy(alpha = 0.32f),
                radius = discR,
                center = Offset(center.x + r * 0.07f, center.y + r * 0.11f)
            )
            drawCircle(Color.White.copy(alpha = 0.95f), radius = discR * 1.13f, center = center)
            if (mark.rank == 1) {
                drawCircle(AccentRose, radius = discR, center = center)
            } else {
                drawCircle(Color.White, radius = discR, center = center)
                drawCircle(
                    Color(0xFF263238),
                    radius = discR,
                    center = center,
                    style = Stroke(width = maxOf(2.5f, r * 0.11f))
                )
            }
            val paint = android.graphics.Paint().apply {
                color = if (mark.rank == 1) android.graphics.Color.WHITE else android.graphics.Color.rgb(38, 50, 56)
                textSize = if (mark.rank == 1) r * 1.02f else r * 0.95f
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
