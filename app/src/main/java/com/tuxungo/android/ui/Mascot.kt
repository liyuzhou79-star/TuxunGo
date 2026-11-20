package com.tuxungo.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The TuxunGo mascot: a glossy black stone with a face, drawn in
 * code (no image asset, no package weight), with a small white
 * stone buddy. Used on the home header, the update guide and
 * empty states so the app has a face of its own.
 */
@Composable
fun Mascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val cx = s * 0.46f
        val cy = s * 0.52f
        val r = s * 0.40f
        // Shadow
        drawCircle(
            Color.Black.copy(alpha = 0.18f),
            radius = r * 0.95f,
            center = Offset(cx + r * 0.08f, cy + r * 1.02f)
        )
        // Body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF5A5A5A), Color(0xFF1B1B1B), Color(0xFF000000)
                ),
                center = Offset(cx - r * 0.35f, cy - r * 0.40f),
                radius = r * 1.3f
            ),
            radius = r,
            center = Offset(cx, cy)
        )
        // Glossy highlight
        drawOval(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(cx - r * 0.52f, cy - r * 0.78f),
            size = Size(r * 0.52f, r * 0.30f)
        )
        // Eyes
        for (ex in listOf(cx - r * 0.34f, cx + r * 0.34f)) {
            drawCircle(Color.White, radius = r * 0.21f, center = Offset(ex, cy - r * 0.05f))
            drawCircle(Color(0xFF141414), radius = r * 0.11f, center = Offset(ex, cy - r * 0.10f))
            drawCircle(Color.White, radius = r * 0.04f, center = Offset(ex - r * 0.03f, cy - r * 0.16f))
        }
        // Smile
        drawArc(
            color = Color.White,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.30f, cy + r * 0.08f),
            size = Size(r * 0.60f, r * 0.44f),
            style = Stroke(width = r * 0.09f)
        )
        // Cheeks
        drawCircle(
            Color(0xFFF48FB1).copy(alpha = 0.55f),
            radius = r * 0.15f,
            center = Offset(cx - r * 0.68f, cy + r * 0.28f)
        )
        drawCircle(
            Color(0xFFF48FB1).copy(alpha = 0.55f),
            radius = r * 0.15f,
            center = Offset(cx + r * 0.68f, cy + r * 0.28f)
        )
        // White stone buddy, bottom right
        val bx = s * 0.82f
        val by = s * 0.80f
        val br = s * 0.15f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F1F1), Color(0xFFD8D8D8)),
                center = Offset(bx - br * 0.3f, by - br * 0.35f),
                radius = br * 1.25f
            ),
            radius = br,
            center = Offset(bx, by)
        )
        drawCircle(
            Color(0xFF9A9A9A).copy(alpha = 0.7f),
            radius = br,
            center = Offset(bx, by),
            style = Stroke(width = s * 0.012f)
        )
        drawCircle(Color(0xFF263238), radius = br * 0.13f, center = Offset(bx - br * 0.32f, by - br * 0.05f))
        drawCircle(Color(0xFF263238), radius = br * 0.13f, center = Offset(bx + br * 0.32f, by - br * 0.05f))
    }
}

/** Small geometric line icons, drawn in the app's own style so the
 *  menus read as graphics, not just text in boxes. */
enum class AppIconKind {
    TRAINING, LIBRARY, MODELS, STATS, CLOCK, SETTINGS, EDIT, GUIDE, TARGET, CHIP,
    APPEARANCE, SOUND, JUDGE
}

@Composable
fun AppIcon(
    kind: AppIconKind,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFD64F7C)
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val sw = s * 0.075f
        val c = Offset(s / 2f, s / 2f)
        when (kind) {
            AppIconKind.TRAINING, AppIconKind.TARGET -> {
                drawCircle(tint, radius = s * 0.42f, center = c, style = Stroke(sw))
                drawCircle(tint, radius = s * 0.24f, center = c, style = Stroke(sw))
                drawCircle(tint, radius = s * 0.07f, center = c)
            }
            AppIconKind.LIBRARY -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.16f, s * 0.24f),
                    size = Size(s * 0.56f, s * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f),
                    style = Stroke(sw)
                )
                drawRoundRect(
                    tint.copy(alpha = 0.55f),
                    topLeft = Offset(s * 0.30f, s * 0.14f),
                    size = Size(s * 0.56f, s * 0.62f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.06f),
                    style = Stroke(sw)
                )
            }
            AppIconKind.MODELS, AppIconKind.CHIP -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.24f, s * 0.24f),
                    size = Size(s * 0.52f, s * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
                    style = Stroke(sw)
                )
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.40f, s * 0.40f),
                    size = Size(s * 0.20f, s * 0.20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f),
                    style = Stroke(sw)
                )
                for (f in listOf(0.35f, 0.5f, 0.65f)) {
                    drawLine(tint, Offset(s * f, s * 0.10f), Offset(s * f, s * 0.24f), sw)
                    drawLine(tint, Offset(s * f, s * 0.76f), Offset(s * f, s * 0.90f), sw)
                    drawLine(tint, Offset(s * 0.10f, s * f), Offset(s * 0.24f, s * f), sw)
                    drawLine(tint, Offset(s * 0.76f, s * f), Offset(s * 0.90f, s * f), sw)
                }
            }
            AppIconKind.STATS -> {
                val base = s * 0.86f
                val heights = listOf(0.30f, 0.52f, 0.40f, 0.66f)
                heights.forEachIndexed { i, hf ->
                    val x = s * (0.14f + i * 0.19f)
                    drawRoundRect(
                        tint,
                        topLeft = Offset(x, base - s * hf),
                        size = Size(s * 0.12f, s * hf),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f)
                    )
                }
                drawLine(tint, Offset(s * 0.08f, base), Offset(s * 0.92f, base), sw)
            }
            AppIconKind.CLOCK -> {
                drawCircle(tint, radius = s * 0.42f, center = c, style = Stroke(sw))
                drawLine(tint, c, Offset(c.x, c.y - s * 0.24f), sw)
                drawLine(tint, c, Offset(c.x + s * 0.18f, c.y + s * 0.06f), sw)
            }
            AppIconKind.SETTINGS -> {
                drawCircle(tint, radius = s * 0.26f, center = c, style = Stroke(sw))
                for (a in 0 until 6) {
                    val ang = Math.toRadians((a * 60).toDouble())
                    val x1 = c.x + kotlin.math.cos(ang).toFloat() * s * 0.30f
                    val y1 = c.y + kotlin.math.sin(ang).toFloat() * s * 0.30f
                    val x2 = c.x + kotlin.math.cos(ang).toFloat() * s * 0.44f
                    val y2 = c.y + kotlin.math.sin(ang).toFloat() * s * 0.44f
                    drawLine(tint, Offset(x1, y1), Offset(x2, y2), sw * 1.2f)
                }
            }
            AppIconKind.EDIT -> {
                for (i in 0..2) {
                    drawLine(
                        tint.copy(alpha = 0.6f),
                        Offset(s * (0.18f + i * 0.32f), s * 0.14f),
                        Offset(s * (0.18f + i * 0.32f), s * 0.86f), sw * 0.7f
                    )
                    drawLine(
                        tint.copy(alpha = 0.6f),
                        Offset(s * 0.14f, s * (0.18f + i * 0.32f)),
                        Offset(s * 0.86f, s * (0.18f + i * 0.32f)), sw * 0.7f
                    )
                }
                drawCircle(Color(0xFF263238), radius = s * 0.11f, center = Offset(s * 0.18f, s * 0.18f))
                drawCircle(Color.White, radius = s * 0.11f, center = Offset(s * 0.82f, s * 0.82f))
                drawCircle(
                    Color(0xFF9A9A9A), radius = s * 0.11f,
                    center = Offset(s * 0.82f, s * 0.82f), style = Stroke(sw * 0.5f)
                )
            }
            AppIconKind.APPEARANCE -> {
                // A yin-yang of black and white stones.
                drawCircle(tint, radius = s * 0.42f, center = c, style = Stroke(sw))
                drawCircle(Color(0xFF263238), radius = s * 0.20f, center = Offset(c.x - s * 0.20f, c.y))
                drawCircle(
                    Color.White, radius = s * 0.20f, center = Offset(c.x + s * 0.20f, c.y)
                )
                drawCircle(
                    Color(0xFF9A9A9A), radius = s * 0.20f,
                    center = Offset(c.x + s * 0.20f, c.y), style = Stroke(sw * 0.5f)
                )
            }
            AppIconKind.SOUND -> {
                // Speaker with two sound arcs.
                drawLine(tint, Offset(s * 0.16f, s * 0.42f), Offset(s * 0.30f, s * 0.42f), sw)
                drawLine(tint, Offset(s * 0.30f, s * 0.42f), Offset(s * 0.48f, s * 0.26f), sw)
                drawLine(tint, Offset(s * 0.48f, s * 0.26f), Offset(s * 0.48f, s * 0.74f), sw)
                drawLine(tint, Offset(s * 0.48f, s * 0.74f), Offset(s * 0.30f, s * 0.58f), sw)
                drawLine(tint, Offset(s * 0.30f, s * 0.58f), Offset(s * 0.16f, s * 0.58f), sw)
                drawArc(
                    tint, startAngle = -50f, sweepAngle = 100f, useCenter = false,
                    topLeft = Offset(s * 0.52f, s * 0.30f),
                    size = Size(s * 0.24f, s * 0.40f), style = Stroke(sw)
                )
                drawArc(
                    tint.copy(alpha = 0.6f), startAngle = -50f, sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(s * 0.62f, s * 0.22f),
                    size = Size(s * 0.28f, s * 0.56f), style = Stroke(sw)
                )
            }
            AppIconKind.JUDGE -> {
                // A balance scale: beam, post and two pans.
                drawLine(tint, Offset(s * 0.14f, s * 0.24f), Offset(s * 0.86f, s * 0.24f), sw)
                drawLine(tint, Offset(c.x, s * 0.24f), Offset(c.x, s * 0.78f), sw)
                drawLine(tint, Offset(s * 0.30f, s * 0.86f), Offset(s * 0.70f, s * 0.86f), sw)
                for (px in listOf(s * 0.22f, s * 0.78f)) {
                    drawLine(tint, Offset(px, s * 0.24f), Offset(px - s * 0.09f, s * 0.52f), sw * 0.8f)
                    drawLine(tint, Offset(px, s * 0.24f), Offset(px + s * 0.09f, s * 0.52f), sw * 0.8f)
                    drawArc(
                        tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(px - s * 0.11f, s * 0.42f),
                        size = Size(s * 0.22f, s * 0.20f), style = Stroke(sw)
                    )
                }
            }
            AppIconKind.GUIDE -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(s * 0.22f, s * 0.12f),
                    size = Size(s * 0.56f, s * 0.76f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.07f),
                    style = Stroke(sw)
                )
                drawLine(tint, Offset(s * 0.36f, s * 0.36f), Offset(s * 0.64f, s * 0.36f), sw)
                drawLine(tint, Offset(s * 0.36f, s * 0.52f), Offset(s * 0.64f, s * 0.52f), sw)
                drawLine(tint, Offset(s * 0.36f, s * 0.68f), Offset(s * 0.56f, s * 0.68f), sw)
            }
        }
    }
}

/**
 * A friendly empty state: mascot, one line of explanation and an
 * optional action. Replaces the old "just a sentence in a box".
 */
@Composable
fun EmptyState(
    title: String,
    desc: String,
    actionText: String = "",
    onAction: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(modifier = Modifier.size(84.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = desc, fontSize = 13.sp, color = Color.Gray)
        if (actionText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}
