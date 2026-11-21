package com.tuxungo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungo.android.go.Stone

/**
 * The app's game-grade visual kit (2.60.0): deep, textured,
 * product-like surfaces — vertical-gradient buttons with a lit
 * top edge and a shaded foot, bordered cards with real depth,
 * a walnut frame for the board and clean geometric glyph icons
 * drawn in code (no image assets, no extra APK size). Everything
 * rides the Material colour scheme, so the three accents and
 * dark mode keep working.
 */

private val ButtonShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(14.dp)

/**
 * Primary action button: filled gradient, a hairline of light
 * on the top edge and a darker foot, pressing dips it slightly.
 * [primary] = false gives the quiet outlined variant.
 */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val top: Color
    val bottom: Color
    val fg: Color
    if (danger) {
        top = Color(0xFFD32F2F)
        bottom = Color(0xFFB71C1C)
        fg = Color.White
    } else if (primary) {
        top = scheme.primary
        bottom = scheme.secondary
        fg = scheme.onPrimary
    } else {
        top = scheme.surfaceVariant
        bottom = scheme.surfaceVariant
        fg = scheme.onSurface
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                val s = if (pressed && enabled) 0.97f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(ButtonShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        top.copy(alpha = if (enabled) 1f else 0.45f),
                        bottom.copy(alpha = if (enabled) 1f else 0.45f)
                    )
                )
            )
            .border(
                1.dp,
                (if (primary) Color.White.copy(alpha = 0.25f)
                else scheme.outline).copy(alpha = if (enabled) 1f else 0.4f),
                ButtonShape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg.copy(alpha = if (enabled) 1f else 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/** A card with product depth: border hairline + soft elevation. */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.border(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
            CardShape
        ),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = content
    )
}

/**
 * Walnut frame for the board: layered wood-tone gradients and
 * an inner bevel line, so the board sits IN something instead
 * of floating on the page. Same wood in light and dark mode.
 */
@Composable
fun BoardFrame(
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!framed) {
        content()
        return
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF8A6238),
                        Color(0xFF6F4C2A),
                        Color(0xFF59391E)
                    )
                )
            )
            .border(
                1.dp, Color(0xFF3E2712),
                RoundedCornerShape(12.dp)
            )
            .padding(9.dp)
    ) {
        Box(
            modifier = Modifier
                .border(
                    1.dp, Color(0xFF2E1C0C).copy(alpha = 0.65f),
                    RoundedCornerShape(4.dp)
                )
        ) {
            content()
        }
    }
}

/** A Go stone drawn as an avatar disc, with a lit top-left. */
@Composable
fun StoneAvatar(stone: Stone, size: Dp = 40.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        if (stone == Stone.BLACK) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF5A5A60), Color(0xFF1B1B1E),
                        Color(0xFF000000)
                    ),
                    center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
                    radius = r * 1.5f
                ),
                radius = r, center = c
            )
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF), Color(0xFFF1EBDD),
                        Color(0xFFCFC4AC)
                    ),
                    center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
                    radius = r * 1.5f
                ),
                radius = r, center = c
            )
            drawCircle(
                color = Color(0xFF9A8F76),
                radius = r - 0.75f, center = c,
                style = Stroke(width = 1.5f)
            )
        }
    }
}

enum class GameIconKind {
    PLAY, VERSUS, TARGET, EYE, BOOK, CLOCK, GEAR, CHART,
    BOARD, MODEL, TROPHY, INFO, IMPORT
}

/**
 * Clean geometric glyph icons, stroked/filled in [tint] on a
 * 24-unit grid — the same visual language everywhere, no emoji,
 * no image files.
 */
@Composable
fun GameIcon(
    kind: GameIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * u)
        fun pt(x: Float, y: Float) = Offset(x * u, y * u)
        when (kind) {
            GameIconKind.PLAY -> {
                val p = Path().apply {
                    moveTo(8f * u, 5f * u)
                    lineTo(19.5f * u, 12f * u)
                    lineTo(8f * u, 19f * u)
                    close()
                }
                drawPath(p, tint)
            }
            GameIconKind.VERSUS -> {
                drawCircle(tint, radius = 5.6f * u, center = pt(9f, 9f))
                drawCircle(
                    tint, radius = 5.6f * u, center = pt(15.5f, 15.5f),
                    style = stroke
                )
            }
            GameIconKind.TARGET -> {
                drawCircle(tint, radius = 9f * u, center = pt(12f, 12f),
                    style = stroke)
                drawCircle(tint, radius = 5.2f * u, center = pt(12f, 12f),
                    style = stroke)
                drawCircle(tint, radius = 1.9f * u, center = pt(12f, 12f))
            }
            GameIconKind.EYE -> {
                val p = Path().apply {
                    moveTo(2.5f * u, 12f * u)
                    quadraticBezierTo(12f * u, 4.5f * u, 21.5f * u, 12f * u)
                    quadraticBezierTo(12f * u, 19.5f * u, 2.5f * u, 12f * u)
                    close()
                }
                drawPath(p, tint, style = stroke)
                drawCircle(tint, radius = 3.4f * u, center = pt(12f, 12f),
                    style = stroke)
                drawCircle(tint, radius = 1.4f * u, center = pt(12f, 12f))
            }
            GameIconKind.BOOK -> {
                drawRoundRect(
                    tint,
                    topLeft = pt(5f, 3.5f),
                    size = Size(14f * u, 17f * u),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(2f * u, 2f * u),
                    style = stroke
                )
                drawLine(tint, pt(9f, 3.5f), pt(9f, 20.5f),
                    strokeWidth = 1.8f * u)
            }
            GameIconKind.CLOCK -> {
                drawCircle(tint, radius = 9f * u, center = pt(12f, 12f),
                    style = stroke)
                drawLine(tint, pt(12f, 12f), pt(12f, 7f),
                    strokeWidth = 1.8f * u)
                drawLine(tint, pt(12f, 12f), pt(15.5f, 14f),
                    strokeWidth = 1.8f * u)
            }
            GameIconKind.GEAR -> {
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val x1 = 12f + 6.8f * Math.cos(a).toFloat()
                    val y1 = 12f + 6.8f * Math.sin(a).toFloat()
                    val x2 = 12f + 9.6f * Math.cos(a).toFloat()
                    val y2 = 12f + 9.6f * Math.sin(a).toFloat()
                    drawLine(tint, pt(x1, y1), pt(x2, y2),
                        strokeWidth = 2.4f * u)
                }
                drawCircle(tint, radius = 6.6f * u, center = pt(12f, 12f),
                    style = stroke)
                drawCircle(tint, radius = 2.4f * u, center = pt(12f, 12f),
                    style = stroke)
            }
            GameIconKind.CHART -> {
                drawRoundRect(
                    tint, topLeft = pt(4f, 12f),
                    size = Size(4.4f * u, 8f * u),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(1.2f * u, 1.2f * u)
                )
                drawRoundRect(
                    tint, topLeft = pt(9.8f, 5f),
                    size = Size(4.4f * u, 15f * u),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(1.2f * u, 1.2f * u)
                )
                drawRoundRect(
                    tint, topLeft = pt(15.6f, 9f),
                    size = Size(4.4f * u, 11f * u),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(1.2f * u, 1.2f * u)
                )
            }
            GameIconKind.BOARD -> {
                drawRoundRect(
                    tint, topLeft = pt(3.5f, 3.5f),
                    size = Size(17f * u, 17f * u),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(2f * u, 2f * u),
                    style = stroke
                )
                drawLine(tint, pt(9.2f, 3.5f), pt(9.2f, 20.5f),
                    strokeWidth = 1.3f * u)
                drawLine(tint, pt(14.8f, 3.5f), pt(14.8f, 20.5f),
                    strokeWidth = 1.3f * u)
                drawLine(tint, pt(3.5f, 9.2f), pt(20.5f, 9.2f),
                    strokeWidth = 1.3f * u)
                drawLine(tint, pt(3.5f, 14.8f), pt(20.5f, 14.8f),
                    strokeWidth = 1.3f * u)
                drawCircle(tint, radius = 1.8f * u, center = pt(9.2f, 9.2f))
            }
            GameIconKind.MODEL -> {
                drawRoundRect(
                    tint, topLeft = pt(6f, 6f),
                    size = Size(12f * u, 12f * u),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(2.5f * u, 2.5f * u),
                    style = stroke
                )
                drawRoundRect(
                    tint, topLeft = pt(10f, 10f),
                    size = Size(4f * u, 4f * u),
                    cornerRadius = androidx.compose.ui.geometry
                        .CornerRadius(1f * u, 1f * u),
                    style = stroke
                )
                for (f in listOf(8.5f, 12f, 15.5f)) {
                    drawLine(tint, pt(f, 6f), pt(f, 3.5f),
                        strokeWidth = 1.6f * u)
                    drawLine(tint, pt(f, 18f), pt(f, 20.5f),
                        strokeWidth = 1.6f * u)
                    drawLine(tint, pt(6f, f), pt(3.5f, f),
                        strokeWidth = 1.6f * u)
                    drawLine(tint, pt(18f, f), pt(20.5f, f),
                        strokeWidth = 1.6f * u)
                }
            }
            GameIconKind.TROPHY -> {
                val cup = Path().apply {
                    moveTo(6.5f * u, 4f * u)
                    lineTo(17.5f * u, 4f * u)
                    lineTo(17.5f * u, 9f * u)
                    quadraticBezierTo(17.5f * u, 14.5f * u, 12f * u, 14.5f * u)
                    quadraticBezierTo(6.5f * u, 14.5f * u, 6.5f * u, 9f * u)
                    close()
                }
                drawPath(cup, tint, style = stroke)
                drawLine(tint, pt(12f, 14.5f), pt(12f, 17.5f),
                    strokeWidth = 1.8f * u)
                drawLine(tint, pt(8f, 20f), pt(16f, 20f),
                    strokeWidth = 1.8f * u)
                drawArc(
                    tint, startAngle = -60f, sweepAngle = 130f,
                    useCenter = false,
                    topLeft = pt(3.2f, 4.8f),
                    size = Size(5.4f * u, 6.4f * u), style = stroke
                )
                drawArc(
                    tint, startAngle = 110f, sweepAngle = 130f,
                    useCenter = false,
                    topLeft = pt(15.4f, 4.8f),
                    size = Size(5.4f * u, 6.4f * u), style = stroke
                )
            }
            GameIconKind.INFO -> {
                drawCircle(tint, radius = 9f * u, center = pt(12f, 12f),
                    style = stroke)
                drawCircle(tint, radius = 1.5f * u, center = pt(12f, 7.6f))
                drawLine(tint, pt(12f, 11f), pt(12f, 16.5f),
                    strokeWidth = 1.9f * u)
            }
            GameIconKind.IMPORT -> {
                drawLine(tint, pt(12f, 3.5f), pt(12f, 14.5f),
                    strokeWidth = 1.9f * u)
                val head = Path().apply {
                    moveTo(6.5f * u, 10f * u)
                    lineTo(12f * u, 15.8f * u)
                    lineTo(17.5f * u, 10f * u)
                }
                drawPath(head, tint, style = stroke)
                drawLine(tint, pt(4.5f, 20.5f), pt(19.5f, 20.5f),
                    strokeWidth = 1.9f * u)
            }
        }
    }
}
