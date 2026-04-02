package org.akvo.afribamodkvalidator.validation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Stamps a quality watermark overlay on validated images.
 *
 * All validated images get a color-coded watermark at the bottom-right:
 * - Green: SHARP (passed)
 * - Yellow: WARNING (borderline, accepted by enumerator)
 * - Red: BLOCKED (rejected)
 *
 * Memory: Decodes with inSampleSize to avoid OOM on large camera photos.
 * EXIF: Preserves key metadata tags after re-encoding.
 */
object ImageWatermark {

    fun stamp(filePath: String, result: BlurDetector.BlurResult): String {
        return try {
            // Read EXIF before modifying the file
            val exifTags = readExifTags(filePath)

            // Decode with inSampleSize if the image is very large
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = true
            }
            val bitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return filePath

            drawWatermark(bitmap, result)

            File(filePath).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            bitmap.recycle()

            // Restore EXIF metadata (orientation already normalized by BlurValidationActivity)
            writeExifTags(filePath, exifTags)

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

    /**
     * Calculate inSampleSize to keep decoded bitmap under ~4MP.
     * This avoids OOM on low-memory devices with large camera photos.
     */
    private fun calculateInSampleSize(width: Int, height: Int): Int {
        val maxPixels = 4_000_000 // 4MP target
        var sample = 1
        var w = width
        var h = height
        while (w * h > maxPixels) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }

    private fun readExifTags(filePath: String): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        try {
            val exif = ExifInterface(filePath)
            for (tag in PRESERVED_EXIF_TAGS) {
                exif.getAttribute(tag)?.let { tags[tag] = it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF tags", e)
        }
        return tags
    }

    private fun writeExifTags(filePath: String, tags: Map<String, String>) {
        if (tags.isEmpty()) return
        try {
            val exif = ExifInterface(filePath)
            for ((tag, value) in tags) {
                exif.setAttribute(tag, value)
            }
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write EXIF tags", e)
        }
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

    private val PRESERVED_EXIF_TAGS = arrayOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH
    )
}
