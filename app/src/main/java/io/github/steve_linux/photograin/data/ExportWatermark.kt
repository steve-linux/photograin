// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class WatermarkCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Serializable
data class ExportWatermark(
    val text: String = "",
    val logo: String? = null,
    val corner: WatermarkCorner = WatermarkCorner.BOTTOM_RIGHT,
    val margin: Float = 0.035f,
    val scale: Float = 0.18f,
    val opacity: Float = 0.65f,
) {
    val active: Boolean get() = logo != null || text.isNotBlank()

    internal fun draw(canvas: Canvas, width: Int, height: Int) {
        if (!active || width <= 0 || height <= 0) return
        val edge = minOf(width, height).toFloat()
        val inset = edge * margin.coerceIn(0f, 0.15f)
        val size = edge * scale.coerceIn(0.025f, 0.4f)
        val alpha = (255 * opacity.coerceIn(0f, 1f)).roundToInt()
        val left = corner == WatermarkCorner.TOP_LEFT || corner == WatermarkCorner.BOTTOM_LEFT
        val top = corner == WatermarkCorner.TOP_LEFT || corner == WatermarkCorner.TOP_RIGHT
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (logo != null) {
            val image = decodeLogo(logo) ?: throw IllegalArgumentException("Invalid watermark image")
            try {
                val factor = size / maxOf(image.width, image.height)
                val w = image.width * factor
                val h = image.height * factor
                val x = if (left) inset else width - inset - w
                val y = if (top) inset else height - inset - h
                paint.alpha = alpha
                canvas.drawBitmap(image, null, RectF(x, y, x + w, y + h), paint)
            } finally {
                image.recycle()
            }
        } else {
            val value = text.trim().take(120)
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.textSize = size / 3f
            val measured = paint.measureText(value)
            if (measured > width - 2 * inset) paint.textSize *= (width - 2 * inset) / measured
            val x = if (left) inset else width - inset - paint.measureText(value)
            val baseline = if (top) inset - paint.ascent() else height - inset - paint.descent()
            paint.color = Color.WHITE
            paint.alpha = alpha
            paint.setShadowLayer((paint.textSize / 15).coerceAtLeast(0.2f), 0f, 1f, Color.argb(alpha, 0, 0, 0))
            canvas.drawText(value, x, baseline, paint)
        }
    }

    private fun decodeLogo(value: String): Bitmap? {
        if (value.length > 500_000) return null
        val data = Base64.decode(value, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth !in 1..256 || bounds.outHeight !in 1..256) return null
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    internal companion object {
        fun importLogo(context: Context, uri: Uri): String {
            val bitmap = requireNotNull(ImageLoader.thumbnail(context, uri, 256))
            try {
                require(bitmap.width <= 256 && bitmap.height <= 256)
                val bytes = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            } finally {
                bitmap.recycle()
            }
        }
    }
}
