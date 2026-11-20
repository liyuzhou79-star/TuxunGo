package com.tuxungo.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.tuxungo.android.go.GoGame
import com.tuxungo.android.go.Stone
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the current board position to a PNG and shares it via
 * the system share sheet. The drawing is done by hand on a Canvas
 * (flat, clean style) so it does not depend on capturing the
 * Compose board view.
 */
object PositionShare {

    fun share(context: Context, game: GoGame, caption: String) {
        runCatching {
            val bitmap = render(game, caption)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "tuxungo_position.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享局面"))
        }
    }

    private fun starPoints(w: Int, h: Int): List<Pair<Int, Int>> = when {
        w == 19 && h == 19 ->
            listOf(3, 9, 15).flatMap { x -> listOf(3, 9, 15).map { y -> x to y } }
        w == 13 && h == 13 ->
            listOf(3 to 3, 9 to 3, 3 to 9, 9 to 9, 6 to 6)
        w == 9 && h == 9 ->
            listOf(2 to 2, 6 to 2, 2 to 6, 6 to 6, 4 to 4)
        else -> emptyList()
    }

    fun render(game: GoGame, caption: String): Bitmap {
        val w = game.size.width
        val h = game.size.height
        val imgW = 1080
        val pad = 76f
        val span = imgW - 2 * pad
        val cell = span / (maxOf(w, h) - 1).coerceAtLeast(1)
        val boardW = cell * (w - 1)
        val boardH = cell * (h - 1)
        val left = (imgW - boardW) / 2f
        val top = pad + 26f
        val captionH = 128
        val imgH = (top + boardH + 44 + captionH).toInt()

        val bitmap = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Board face with a soft vertical wood gradient.
        val faceRect = RectF(
            left - cell * 0.52f, top - cell * 0.52f,
            left + boardW + cell * 0.52f, top + boardH + cell * 0.52f
        )
        paint.shader = LinearGradient(
            faceRect.left, faceRect.top, faceRect.left, faceRect.bottom,
            Color.rgb(238, 199, 110), Color.rgb(224, 172, 78),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(faceRect, 18f, 18f, paint)
        paint.shader = null
        paint.color = Color.rgb(150, 104, 44)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRoundRect(faceRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        fun px(x: Int) = left + x * cell
        fun py(y: Int) = top + y * cell

        // Grid.
        paint.color = Color.rgb(90, 58, 20)
        paint.strokeWidth = 2f
        for (x in 0 until w) {
            canvas.drawLine(px(x), py(0), px(x), py(h - 1), paint)
        }
        for (y in 0 until h) {
            canvas.drawLine(px(0), py(y), px(w - 1), py(y), paint)
        }
        paint.strokeWidth = 4.5f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(
            RectF(px(0), py(0), px(w - 1), py(h - 1)), paint
        )
        paint.style = Paint.Style.FILL
        // Star points.
        paint.color = Color.rgb(90, 58, 20)
        for ((sx, sy) in starPoints(w, h)) {
            canvas.drawCircle(px(sx), py(sy), cell * 0.11f, paint)
        }
        // Coordinates: letters skip I, numbers count from the top.
        paint.color = Color.rgb(120, 92, 50)
        paint.textSize = cell * 0.42f
        paint.textAlign = Paint.Align.CENTER
        val letters = "ABCDEFGHJKLMNOPQRSTUVWXYZ"
        for (x in 0 until w) {
            canvas.drawText(
                letters[x].toString(), px(x), top - cell * 0.68f, paint
            )
        }
        for (y in 0 until h) {
            canvas.drawText(
                (h - y).toString(), left - cell * 0.78f,
                py(y) + cell * 0.15f, paint
            )
        }

        // Stones with a soft shadow and a radial sheen.
        val last = game.lastMove()
        val r = cell * 0.47f
        for (y in 0 until h) {
            for (x in 0 until w) {
                val stone = game.stoneAt(x, y) ?: continue
                val cx = px(x)
                val cy = py(y)
                paint.shader = null
                paint.color = Color.argb(70, 60, 40, 10)
                canvas.drawCircle(cx + r * 0.07f, cy + r * 0.13f, r, paint)
                paint.shader = if (stone == Stone.BLACK) {
                    RadialGradient(
                        cx - r * 0.35f, cy - r * 0.35f, r * 1.5f,
                        Color.rgb(96, 96, 104), Color.rgb(8, 8, 10),
                        Shader.TileMode.CLAMP
                    )
                } else {
                    RadialGradient(
                        cx - r * 0.35f, cy - r * 0.35f, r * 1.5f,
                        Color.rgb(255, 255, 255), Color.rgb(203, 203, 208),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(cx, cy, r, paint)
                paint.shader = null
                if (stone == Stone.WHITE) {
                    paint.color = Color.rgb(150, 150, 156)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.6f
                    canvas.drawCircle(cx, cy, r - 0.8f, paint)
                    paint.style = Paint.Style.FILL
                }
                if (last != null && !last.isPass && last.x == x && last.y == y) {
                    paint.color = if (stone == Stone.BLACK) {
                        Color.WHITE
                    } else {
                        Color.rgb(30, 30, 34)
                    }
                    canvas.drawCircle(cx, cy, r * 0.26f, paint)
                }
            }
        }

        // Caption strip.
        val capY = top + boardH + 96f
        paint.color = Color.rgb(38, 34, 30)
        paint.textSize = 41f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(caption, pad - 10f, capY, paint)
        paint.color = Color.rgb(150, 144, 136)
        paint.textSize = 34f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TuxunGo", imgW - pad + 10f, capY, paint)
        return bitmap
    }
}
