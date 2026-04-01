package org.akvo.afribamodkvalidator.validation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

/**
 * Detects image blur using manual Laplacian variance computation.
 * No OpenCV dependency — uses standard Android Bitmap API.
 *
 * Algorithm:
 * 1. Downscale to max dimension (preserving aspect ratio)
 * 2. Convert to grayscale using luminance formula
 * 3. Apply 3x3 Laplacian kernel convolution
 * 4. Compute variance of the Laplacian output
 * 5. Classify as SHARP, BORDERLINE, or VERY_BLURRY
 */
class BlurDetector(
    private val warnThreshold: Double = DEFAULT_WARN_THRESHOLD,
    private val blockThreshold: Double = DEFAULT_BLOCK_THRESHOLD,
    private val maxDimension: Int = DEFAULT_MAX_DIMENSION
) {

    enum class BlurLevel { SHARP, BORDERLINE, VERY_BLURRY }

    data class BlurResult(
        val varianceScore: Double,
        val level: BlurLevel,
        val warnThreshold: Double,
        val blockThreshold: Double
    )

    fun detect(bitmap: Bitmap): BlurResult {
        val scaled = downscale(bitmap, maxDimension)
        val grayscale = toGrayscale(scaled)
        val width = scaled.width
        val height = scaled.height
        if (scaled != bitmap) scaled.recycle()

        val laplacian = applyLaplacianKernel(grayscale, width, height)
        val variance = computeVariance(laplacian)

        val level = when {
            variance < blockThreshold -> BlurLevel.VERY_BLURRY
            variance < warnThreshold -> BlurLevel.BORDERLINE
            else -> BlurLevel.SHARP
        }
        return BlurResult(variance, level, warnThreshold, blockThreshold)
    }

    fun detectFromFile(filePath: String): BlurResult? {
        val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
        val result = detect(bitmap)
        bitmap.recycle()
        return result
    }

    internal fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap

        val scale = maxDim.toDouble() / maxOf(width, height)
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    internal fun toGrayscale(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val grayscale = IntArray(width * height)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            // Luminance formula: 0.299R + 0.587G + 0.114B
            grayscale[i] = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
        }
        return grayscale
    }

    internal fun applyLaplacianKernel(
        grayscale: IntArray,
        width: Int,
        height: Int
    ): DoubleArray {
        // Laplacian kernel: [[0,1,0],[1,-4,1],[0,1,0]]
        val outputSize = (width - 2) * (height - 2)
        if (outputSize <= 0) return doubleArrayOf()

        val output = DoubleArray(outputSize)
        var idx = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = grayscale[y * width + x]
                val top = grayscale[(y - 1) * width + x]
                val bottom = grayscale[(y + 1) * width + x]
                val left = grayscale[y * width + (x - 1)]
                val right = grayscale[y * width + (x + 1)]
                output[idx++] = (top + bottom + left + right - 4.0 * center)
            }
        }
        return output
    }

    internal fun computeVariance(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val n = values.size.toDouble()
        var sum = 0.0
        var sumSq = 0.0
        for (v in values) {
            sum += v
            sumSq += v * v
        }
        // Variance = E[X^2] - (E[X])^2
        val mean = sum / n
        return (sumSq / n) - (mean * mean)
    }

    companion object {
        const val DEFAULT_WARN_THRESHOLD = 100.0
        const val DEFAULT_BLOCK_THRESHOLD = 50.0
        const val DEFAULT_MAX_DIMENSION = 500
    }
}
