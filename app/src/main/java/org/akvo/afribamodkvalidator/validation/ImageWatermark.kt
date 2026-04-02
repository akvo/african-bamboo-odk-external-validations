package org.akvo.afribamodkvalidator.validation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import java.io.File

/**
 * Stamps a quality watermark overlay on validated images.
 *
 * All validated images get a color-coded watermark at the bottom-right:
 * - Green: SHARP ✓ (passed)
 * - Yellow: WARNING ⚠ (borderline, accepted by enumerator)
 * - Red: BLOCKED ✗ (rejected)
 *
 * This lets supervisors identify borderline photos at a glance.
 */
object ImageWatermark {

    fun stamp(filePath: String, result: BlurDetector.BlurResult): String {
        return try {
            val original = android.graphics.BitmapFactory.decodeFile(filePath) ?: return filePath
            val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
            original.recycle()

            drawWatermark(mutable, result)

            File(filePath).outputStream().use { out ->
                mutable.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            mutable.recycle()

            Log.d(TAG, "Watermark stamped: ${formatLabel(result)}")
            filePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stamp watermark", e)
            filePath
        }
    }

    internal fun drawWatermark(bitmap: Bitmap, result: BlurDetector.BlurResult) {
        val canvas = Canvas(bitmap)
        val textSize = bitmap.width * 0.03f
        val padding = textSize * 0.5f

        val label = formatLabel(result)
        val bgColor = when (result.level) {
            BlurDetector.BlurLevel.SHARP -> Color.argb(180, 0, 120, 0)
            BlurDetector.BlurLevel.BORDERLINE -> Color.argb(180, 200, 150, 0)
            BlurDetector.BlurLevel.VERY_BLURRY -> Color.argb(180, 180, 0, 0)
        }

        val bgPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            isFakeBoldText = true
        }

        val textWidth = textPaint.measureText(label)
        val bgLeft = bitmap.width - textWidth - padding * 2
        val bgTop = bitmap.height - textSize - padding * 3

        canvas.drawRect(
            bgLeft,
            bgTop,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            bgPaint
        )

        canvas.drawText(
            label,
            bgLeft + padding,
            bitmap.height - padding,
            textPaint
        )
    }

    private fun formatLabel(result: BlurDetector.BlurResult): String {
        val levelTag = when (result.level) {
            BlurDetector.BlurLevel.SHARP -> "[SHARP] \u2713"
            BlurDetector.BlurLevel.BORDERLINE -> "[WARNING] \u26A0"
            BlurDetector.BlurLevel.VERY_BLURRY -> "[BLOCKED] \u2717"
        }

        return if (result.method == BlurDetector.METHOD_OCR) {
            "Q:${"%.2f".format(result.score)} ${result.method} E:${result.elementCount} $levelTag"
        } else {
            "Q:${"%.0f".format(result.score)} ${result.method} $levelTag"
        }
    }

    private const val TAG = "ImageWatermark"
}
