package com.tuxungo.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** The five stone corner badges, one calligraphy character each. */
enum class StoneBadgeKind(val char: String) {
    /** Red: the move that sealed the whole game. */
    JUE("绝"),
    /** Blue: the move that settled one region / dragon. */
    DING("定"),
    /** Red: a deliberate blunder by the AI. */
    EVIL("恶"),
    /** White: a deliberate vulgar move by the AI. */
    VULGAR("俗"),
    /** Green: the move that resolved a ko. */
    KO("销")
}

/** One corner badge pinned on one played stone. */
data class StoneBadge(val move: Move, val kind: StoneBadgeKind)

/** Disc fill, rim, ink colour and breathing for one badge kind. */
private data class BadgePalette(
    val fill: Color,
    val rim: Color,
    val ink: Int,
    val breathe: Boolean
)

/** Board face designs, selectable in settings. */
enum class BoardSkin {
    BEIGE, KAYA, WOOD_LIGHT, WOOD_DARK, BAMBOO, SAKURA, MOON, OBSIDIAN;

    companion object {
        fun fromId(id: String): BoardSkin = when (id) {
            "wood_dark" -> WOOD_DARK
            "bamboo" -> BAMBOO
            "sakura" -> SAKURA
            "moon" -> MOON
            "obsidian" -> OBSIDIAN
            "wood_light" -> WOOD_LIGHT
            "kaya" -> KAYA
            else -> BEIGE
        }
    }
}

/**
 * "Board fills the screen" mode: the board's parent column keeps a
 * small horizontal padding for its other content; this widens just
 * the board by that padding on both sides and shifts it back, so
 * the wooden face reaches the screen edges while everything else
 * keeps its margins. The board stays square — proportions are
 * never distorted.
 */
fun Modifier.stretchToParent(enabled: Boolean, parentPadding: Dp): Modifier =
    if (!enabled) {
        this
    } else {
        this.layout { measurable, constraints ->
            val extra = (parentPadding * 2).roundToPx()
            val w = constraints.maxWidth + extra
            val placeable = measurable.measure(
                constraints.copy(minWidth = w, maxWidth = w)
            )
            layout(w, placeable.height) {
                placeable.place(-extra / 2, 0)
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
    judgedDead: Set<Int>? = null,
    /** Draw each stone's move number on it. */
    showMoveNumbers: Boolean = false,
    /** One-colour Go: render every stone black. */
    oneColor: Boolean = false,
    /** The human-style model's favourite point: drawn as a blue ring
     *  so it reads differently from the AI's numbered discs. */
    humanPoint: Pair<Int, Int>? = null,
    /** The human-style model's top moves as ranked marks: drawn as
     *  orange-red discs, distinct from both the AI's discs and the
     *  blue humanPoint ring. */
    humanMarks: List<CandidateMark> = emptyList(),
    /** Corner badges on stones (「绝 / 定 / 恶 / 俗 / 销」):
     *  each badged stone keeps a small flat disc with a white (or
     *  dark, on the white 「俗」 disc) calligraphy character at
     *  its top-right corner for the rest of the game. Callers pass
     *  at most one badge per stone. */
    badges: List<StoneBadge> = emptyList(),
    /** The analysis vulgar pick （俗）: an (empty) candidate
     *  point that gets a small white calligraphy 「俗」 disc at
     *  its top-right corner — the same badge language as the
     *  stone badges, riding on a candidate instead of a stone.
     *  Drawn only when the point is among [candidates]. */
    vulgarPoint: Move? = null,
    /** Territory colouring starts at this ownership strength: points
     *  weaker than this stay uncoloured, so early-game boards do not
     *  speckle. Adjustable in the judgment settings. */
    territorySpread: Float = 0.30f,
    /** Territory colouring strength multiplier (0.4..1). */
    territoryIntensity: Float = 1.0f,
    /** Render the board rotated 180 degrees (White's point of view). */
    flipped: Boolean = false,
    /** Placement magnifier (enlarged 5x5 corner inset while a point
     *  is previewed). Off by default; a settings toggle turns it on. */
    showMagnifier: Boolean = false,
    /** Stretch the board face to the screen edges, compensating the
     *  parent column's horizontal padding. */
    edgeToEdge: Boolean = false,
    parentPadding: Dp = 0.dp,
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
            BoardSkin.BEIGE -> "board/beige.jpg"
            BoardSkin.KAYA -> "board/kaya_real.jpg"
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
    val darkSkin = skin == BoardSkin.WOOD_DARK || skin == BoardSkin.OBSIDIAN
    val ink = if (darkSkin) GridInkOnDark else GridInk
    val coordInk = if (darkSkin) CoordInkOnDark else CoordInk
    // Placement ripple: restarts whenever a new move lands.
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(game.moves.size) {
        if (game.moves.isNotEmpty()) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, animationSpec = tween(450))
        }
    }
    // The last-move ring used to breathe via an infinite
    // transition (2.66.0: removed — an infinite animation makes
    // the whole board canvas redraw every frame for the entire
    // game, which costs battery and smoothness for a purely
    // decorative pulse. The ring is now a steady marker; the
    // one-shot placement ripple still animates each new stone.)
    // Candidate / human discs grow in softly when a fresh analysis
    // lands, instead of snapping onto the board.
    val marksAnim = remember { Animatable(1f) }
    LaunchedEffect(candidates, humanMarks) {
        if (candidates.isNotEmpty() || humanMarks.isNotEmpty()) {
            marksAnim.snapTo(0.55f)
            marksAnim.animateTo(1f, animationSpec = tween(170))
        }
    }
    // Slow pulse for the 「绝」 badge —
    // deliberately gentle, so it never shouts over the play.
    val pulseTransition = rememberInfiniteTransition(label = "markPulse")
    val markPulse by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "markPulse"
    )
    // Calligraphy typeface for the 「绝」 badge (a two-glyph subset
    // of Ma Shan Zheng, see THIRD_PARTY_NOTICES.md). Falls back to
    // the default bold face when the font cannot be loaded.
    val jueTypeface = remember {
        try {
            context.resources.getFont(com.tuxungo.android.R.font.jue_calligraphy)
        } catch (_: Exception) {
            null
        }
    }
    Canvas(
        modifier = Modifier
            .stretchToParent(edgeToEdge, parentPadding)
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(game, enabled, showCoordinates, flipped) {
                detectTapGestures { offset ->
                    if (!enabled) return@detectTapGestures
                    val label = size.width * if (showCoordinates) 0.052f else 0.018f
                    val cellX = (size.width - 2 * label) / w
                    val cellY = (size.height - 2 * label) / h
                    val originX = label + cellX / 2f
                    val originY = label + cellY / 2f
                    val fx = (offset.x - originX) / cellX
                    val fy = (offset.y - originY) / cellY
                    val gx = fx.roundToInt()
                    val gy = fy.roundToInt()
                    // Snap to the nearest intersection within half a cell:
                    // forgiving for fingers, no dead zones between points.
                    if (gx in 0 until w && gy in 0 until h &&
                        kotlin.math.abs(fx - gx) <= 0.5f && kotlin.math.abs(fy - gy) <= 0.5f
                    ) {
                        // Undo the 180-degree rotation when flipped.
                        val ix = if (flipped) w - 1 - gx else gx
                        val iy = if (flipped) h - 1 - gy else gy
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
        // When flipped, logical points are drawn rotated 180 degrees;
        // every marker, label and stone goes through px/py, so the
        // whole board (including coordinates) follows automatically.
        fun px(x: Int) = originX + (if (flipped) w - 1 - x else x) * cellX
        fun py(y: Int) = originY + (if (flipped) h - 1 - y else y) * cellY

        // The board's outer corners in screen space. When flipped,
        // px(0) is the RIGHT edge and py(0) the BOTTOM one, so the face
        // geometry must come from min/max — computing it from px(w-1)
        // directly collapsed the face to a sliver in flipped view.
        val gridLeft = minOf(px(0), px(w - 1))
        val gridRight = maxOf(px(0), px(w - 1))
        val gridTop = minOf(py(0), py(h - 1))
        val gridBottom = maxOf(py(0), py(h - 1))
        val faceLeft = gridLeft - cellX * 0.64f
        val faceTop = gridTop - cellY * 0.64f
        val faceSize = Size(
            gridRight - gridLeft + cellX * 1.28f,
            gridBottom - gridTop + cellY * 1.28f
        )
        val corner = CornerRadius(s * 0.014f, s * 0.014f)
        val edgeColor = when (skin) {
            BoardSkin.BEIGE -> Color(0xFFC49A58)
            BoardSkin.KAYA -> Color(0xFF9C7434)
            BoardSkin.WOOD_LIGHT -> Color(0xFFB0823F)
            BoardSkin.WOOD_DARK -> Color(0xFF2E1B0E)
            BoardSkin.BAMBOO -> Color(0xFF9A6B2F)
            BoardSkin.SAKURA -> Color(0xFFD98CA6)
            BoardSkin.MOON -> Color(0xFFB4AB9C)
            BoardSkin.OBSIDIAN -> Color(0xFF171B21)
        }
        val grainInk = when (skin) {
            BoardSkin.SAKURA -> Color(0xFFC48AA0)
            BoardSkin.MOON -> Color(0xFF8F887B)
            BoardSkin.OBSIDIAN -> Color(0xFF7C8694)
            else -> Color(0xFF8A5A24)
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
                BoardSkin.MOON -> Color(0xFFF5F2EC) to Color(0xFFE7E1D3)
                BoardSkin.OBSIDIAN -> Color(0xFF3B434E) to Color(0xFF272C34)
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
                        color = grainInk.copy(alpha = 0.028f + alphaSeed * 0.035f),
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
            topLeft = Offset(gridLeft, gridTop),
            size = Size(gridRight - gridLeft, gridBottom - gridTop),
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

        // Territory marks: squares in the owner's color on empty
        // points, shaded by how strongly the engine believes the
        // point belongs to that side — a pale small square for a
        // slight lean, a big dark one for settled territory. Points
        // weaker than territorySpread stay uncoloured (the user
        // controls how far the colouring "spreads" in settings).
        if (ownership != null) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (game.stoneAt(x, y) != null) continue
                    val v = ownership.getOrNull(y * w + x) ?: continue
                    val mag = kotlin.math.abs(v).toFloat()
                    if (mag < territorySpread) continue
                    val t = ((mag - territorySpread) /
                        (1f - territorySpread)).coerceIn(0f, 1f)
                    val alpha = ((0.22f + 0.78f * t) * territoryIntensity)
                        .coerceIn(0f, 1f)
                    val half = minOf(cellX, cellY) * 0.19f * (0.5f + 0.5f * t)
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

        // Stones with shadows and gentle radial shading. [scale]
        // lets the newest stone settle in slightly oversized.
        fun drawStone(cx: Float, cy: Float, stone: Stone, alpha: Float, scale: Float = 1f) {
            drawCircle(
                Color.Black.copy(alpha = 0.26f * alpha),
                radius = r * 0.97f * scale,
                center = Offset(cx + r * 0.10f, cy + r * 0.17f)
            )
            val brush = if (stone == Stone.BLACK) {
                Brush.radialGradient(
                    colors = listOf(Color(0xFF4D4D4D), Color(0xFF161616), Color(0xFF000000)),
                    center = Offset(cx - r * 0.30f, cy - r * 0.36f),
                    radius = r * 1.25f * scale
                )
            } else {
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF5F5F5), Color(0xFFD9D9D9)),
                    center = Offset(cx - r * 0.28f, cy - r * 0.34f),
                    radius = r * 1.25f * scale
                )
            }
            drawCircle(brush = brush, radius = r * scale, center = Offset(cx, cy), alpha = alpha)
            if (stone == Stone.WHITE) {
                drawCircle(
                    Color(0xFF9A9A9A).copy(alpha = 0.6f * alpha),
                    radius = r * scale, center = Offset(cx, cy),
                    style = Stroke(width = maxOf(1f, r * 0.045f))
                )
            }
        }
        // The newest stone on the board (for the settle-in scale).
        val lastMoveStone = game.lastMove()
        // Latest move number per point, for the move-number overlay.
        val moveNumbers: Map<Int, Pair<Int, Stone>> = if (showMoveNumbers) {
            val m = HashMap<Int, Pair<Int, Stone>>()
            game.moves.forEachIndexed { i, mv ->
                if (!mv.isPass) m[game.index(mv.x, mv.y)] = (i + 1) to mv.stone
            }
            m
        } else {
            emptyMap()
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val stone = game.stoneAt(x, y) ?: continue
                val dead = game.index(x, y) in markedDead
                val renderStone = if (oneColor) Stone.BLACK else stone
                val isNewest = lastMoveStone != null && !lastMoveStone.isPass &&
                    lastMoveStone.x == x && lastMoveStone.y == y &&
                    lastMoveStone.stone == stone
                drawStone(
                    px(x), py(y), renderStone, if (dead) 0.45f else 1f,
                    scale = if (isNewest) {
                        1f + 0.15f * (1f - ripple.value)
                    } else {
                        1f
                    }
                )
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
                    // When the caller supplies its own judged-dead
                    // set (the same set the numbers are counted
                    // from), use it alone so board and panel agree.
                    val dead = if (judgedDead != null) {
                        game.index(x, y) in judgedDead
                    } else {
                        (stone == Stone.WHITE && v > territoryThreshold) ||
                            (stone == Stone.BLACK && v < -territoryThreshold)
                    }
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
        val last = lastMoveStone
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
            // Steady ring + the placement ripple on the newest stone.
            drawCircle(
                color = Color(0xFF2F7CF6).copy(alpha = 0.34f),
                radius = r * 1.10f,
                center = Offset(cx, cy),
                style = Stroke(width = maxOf(1.2f, r * 0.05f))
            )
            if (ripple.value < 1f) {
                drawCircle(
                    color = Color(0xFF2F7CF6).copy(
                        alpha = 0.45f * (1f - ripple.value)
                    ),
                    radius = r * (0.7f + 0.9f * ripple.value),
                    center = Offset(cx, cy),
                    style = Stroke(width = maxOf(1.5f, r * 0.07f))
                )
            }
        }

        // Move numbers on the stones (latest move at each point).
        if (showMoveNumbers) {
            for ((idx, numStone) in moveNumbers) {
                val (num, moveStone) = numStone
                val x = idx % w
                val y = idx / w
                if (game.stoneAt(x, y) != moveStone) continue
                val renderStone = if (oneColor) Stone.BLACK else moveStone
                val paint = android.graphics.Paint().apply {
                    color = if (renderStone == Stone.BLACK) {
                        android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.rgb(38, 50, 56)
                    }
                    textSize = if (num >= 100) r * 0.58f else r * 0.78f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val metrics = paint.fontMetrics
                val baseline = py(y) - (metrics.ascent + metrics.descent) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    num.toString(), px(x), baseline, paint
                )
            }
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
        // Candidate markers (2.65.0: the whole style was rebuilt
        // at the user's demand — no gradients, no gloss, no glow,
        // no shadows: flat matte printed discs, one flat colour
        // and a crisp thin edge, like markers printed on a real
        // board). Colour code, shared with the floating pill:
        //   human top choice        -> purple
        //   AI top choice           -> light yellow
        //   both tops on one point  -> orange
        //   every other rank        -> white
        // When an AI mark and a human mark share a point, the
        // higher-priority colour wins and is the only one drawn:
        // both-tops > human top > AI top > the better white rank.
        fun androidx.compose.ui.graphics.drawscope.DrawScope.flatMark(
            x: Int,
            y: Int,
            fill: Color,
            edge: Color,
            numberColor: Int,
            radiusFactor: Float,
            label: String
        ) {
            val center = Offset(px(x), py(y))
            val discR = r * radiusFactor * marksAnim.value
            drawCircle(fill, radius = discR, center = center)
            drawCircle(
                edge,
                radius = discR,
                center = center,
                style = Stroke(width = maxOf(1.5f, r * 0.05f))
            )
            val paint = android.graphics.Paint().apply {
                color = numberColor
                textSize = r * 0.72f * marksAnim.value
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                isAntiAlias = true
            }
            val metrics = paint.fontMetrics
            val baseline = center.y - (metrics.ascent + metrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText(
                label, center.x, baseline, paint
            )
        }

        val aiByPoint = candidates.associateBy { it.x to it.y }
        val humanByPoint = humanMarks.associateBy { it.x to it.y }
        for (pt in (aiByPoint.keys + humanByPoint.keys)) {
            val ai = aiByPoint[pt]
            val hu = humanByPoint[pt]
            when {
                ai?.rank == 1 && hu?.rank == 1 -> flatMark(
                    pt.first, pt.second,
                    Color(0xFFF57C00), Color(0xFFE65100),
                    android.graphics.Color.WHITE, 0.95f, "1"
                )
                hu?.rank == 1 -> flatMark(
                    pt.first, pt.second,
                    Color(0xFF7B1FA2), Color(0xFF4A148C),
                    android.graphics.Color.WHITE, 0.95f, "1"
                )
                ai?.rank == 1 -> flatMark(
                    pt.first, pt.second,
                    Color(0xFFFFE082), Color(0xFFC49000),
                    android.graphics.Color.rgb(78, 52, 46), 0.95f, "1"
                )
                else -> {
                    val rank = minOf(ai?.rank ?: 99, hu?.rank ?: 99)
                    flatMark(
                        pt.first, pt.second,
                        Color(0xFFFFFFFF), Color(0xFF90A4AE),
                        android.graphics.Color.rgb(55, 71, 79),
                        if (rank <= 3) 0.80f else 0.68f, "$rank"
                    )
                }
            }
        }
        // The analysis vulgar pick （俗）: a small blue disc
        // with a white calligraphy 「俗」 at the candidate
        // point's top-right corner — same badge language as the
        // stone badges, but riding on an (empty) candidate
        // point. Blue is the vulgar colour (user's ruling).
        // No membership check against the displayed candidates:
        // when a vulgar pick exists the display intentionally
        // simplifies to the top move plus this badge, so the
        // vulgar point is usually NOT among the shown marks.
        // GameScreen only passes a point while analysis marks
        // are on display at all.
        if (vulgarPoint != null) {
            val c = Offset(px(vulgarPoint.x), py(vulgarPoint.y))
            val br = r * 0.34f
            val bc = c + Offset(r * 0.52f, -r * 0.52f)
            drawCircle(Color(0xFF1565C0), radius = br, center = bc)
            drawCircle(
                Color(0xFF0D47A1), radius = br, center = bc,
                style = Stroke(width = maxOf(1.2f, r * 0.045f))
            )
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = br * 1.3f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = jueTypeface
                if (jueTypeface == null) isFakeBoldText = true
            }
            val metrics = paint.fontMetrics
            val baseline = bc.y - (metrics.ascent + metrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText(
                "俗", bc.x, baseline, paint
            )
        }

        // Human-style favourite: a blue double ring, no number — the
        // "where a human would play" marker, distinct from the AI's
        // ranked discs even when both point at the same spot.
        if (humanPoint != null) {
            val center = Offset(px(humanPoint.first), py(humanPoint.second))
            drawCircle(
                Color(0xFF2F7CF6),
                radius = r * 1.02f,
                center = center,
                style = Stroke(width = maxOf(2.5f, r * 0.10f))
            )
            drawCircle(
                Color(0xFF2F7CF6).copy(alpha = 0.55f),
                radius = r * 0.82f,
                center = center,
                style = Stroke(width = maxOf(1.5f, r * 0.05f))
            )
        }

        // Stone corner badges (「绝 / 定 / 恶 / 俗 / 销」): a
        // small flat disc at the stone's top-right corner with one
        // calligraphy character, staying for the rest of the game.
        // Colours: 绝 red, 定 blue, 恶 red, 俗 blue with a white
        // character, 销 green. Only 「绝」 breathes very gently
        // with markPulse — present, but never shouting over play.
        for (badge in badges) {
            val mv = badge.move
            if (mv.isPass || game.stoneAt(mv.x, mv.y) != mv.stone) {
                continue
            }
            val (fill, rim, ink, breathe) = when (badge.kind) {
                StoneBadgeKind.JUE -> BadgePalette(
                    Color(0xFFC62828), Color(0xFF7F1010),
                    android.graphics.Color.WHITE, true
                )
                StoneBadgeKind.DING -> BadgePalette(
                    Color(0xFF1565C0), Color(0xFF0D47A1),
                    android.graphics.Color.WHITE, false
                )
                StoneBadgeKind.EVIL -> BadgePalette(
                    Color(0xFFC62828), Color(0xFF7F1010),
                    android.graphics.Color.WHITE, false
                )
                StoneBadgeKind.VULGAR -> BadgePalette(
                    Color(0xFF1565C0), Color(0xFF0D47A1),
                    android.graphics.Color.WHITE, false
                )
                StoneBadgeKind.KO -> BadgePalette(
                    Color(0xFF2E7D32), Color(0xFF1B5E20),
                    android.graphics.Color.WHITE, false
                )
            }
            val center = Offset(px(mv.x), py(mv.y))
            val badgeCenter = Offset(
                center.x + r * 0.55f, center.y - r * 0.55f
            )
            drawCircle(fill, radius = r * 0.47f, center = badgeCenter)
            drawCircle(
                rim.copy(alpha = 0.8f),
                radius = r * 0.47f,
                center = badgeCenter,
                style = Stroke(width = maxOf(1.2f, r * 0.045f))
            )
            val paint = android.graphics.Paint().apply {
                color = ink
                textSize = r * 0.8f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                if (breathe) {
                    alpha = (218 + (37 * markPulse).toInt()).coerceIn(0, 255)
                }
                if (jueTypeface != null) {
                    typeface = jueTypeface
                } else {
                    isFakeBoldText = true
                }
            }
            val metrics = paint.fontMetrics
            val baseline = badgeCenter.y - (metrics.ascent + metrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText(
                badge.kind.char, badgeCenter.x, baseline, paint
            )
        }

        // Placement magnifier: while a point is selected (tap-to-
        // confirm preview), show an enlarged 5x5 neighbourhood in
        // the corner farthest from the point, so on dense boards
        // the exact spot and its surroundings are easy to verify.
        if (preview != null && showMagnifier) {
            val (pvx, pvy) = preview
            val magSize = minOf(size.width, size.height) * 0.38f
            val mCell = magSize / 5f
            val mLeft = if (pvx < w / 2) size.width - magSize - 8f else 8f
            val mTop = if (pvy < h / 2) size.height - magSize - 8f else 8f
            drawRoundRect(
                color = Color(0xFFF3D9A4),
                topLeft = Offset(mLeft, mTop),
                size = Size(magSize, magSize),
                cornerRadius = CornerRadius(10f, 10f)
            )
            val mcx = mLeft + magSize / 2f
            val mcy = mTop + magSize / 2f
            clipRect(
                left = mLeft, top = mTop,
                right = mLeft + magSize, bottom = mTop + magSize
            ) {
                for (i in -2..2) {
                    if (pvx + i in 0 until w) {
                        drawLine(
                            Color(0xFF5A3C1E),
                            Offset(mcx + i * mCell, mTop),
                            Offset(mcx + i * mCell, mTop + magSize),
                            strokeWidth = 1.4f
                        )
                    }
                    if (pvy + i in 0 until h) {
                        drawLine(
                            Color(0xFF5A3C1E),
                            Offset(mLeft, mcy + i * mCell),
                            Offset(mLeft + magSize, mcy + i * mCell),
                            strokeWidth = 1.4f
                        )
                    }
                }
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val bx = pvx + dx
                        val by = pvy + dy
                        if (bx !in 0 until w || by !in 0 until h) continue
                        val st = game.stoneAt(bx, by) ?: continue
                        val c = Offset(mcx + dx * mCell, mcy + dy * mCell)
                        drawCircle(
                            if (st == Stone.BLACK) {
                                Color(0xFF1C1C1C)
                            } else {
                                Color(0xFFF6F6F6)
                            },
                            radius = mCell * 0.46f, center = c
                        )
                        if (st == Stone.WHITE) {
                            drawCircle(
                                Color(0xFF969696),
                                radius = mCell * 0.46f, center = c,
                                style = Stroke(width = 1.2f)
                            )
                        }
                    }
                }
                if (game.stoneAt(pvx, pvy) == null) {
                    drawCircle(
                        if (game.current == Stone.BLACK) {
                            Color.Black.copy(alpha = 0.55f)
                        } else {
                            Color.White.copy(alpha = 0.65f)
                        },
                        radius = mCell * 0.46f, center = Offset(mcx, mcy)
                    )
                }
                drawCircle(
                    AccentRose,
                    radius = mCell * 0.56f, center = Offset(mcx, mcy),
                    style = Stroke(width = 2.6f)
                )
            }
            drawRoundRect(
                color = Color(0xFF8A5A24),
                topLeft = Offset(mLeft, mTop),
                size = Size(magSize, magSize),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2.5f)
            )
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
