package org.akvo.afribamodkvalidator.validation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Benchmark test for blur detection using real photos from fixtures/.
 *
 * Runs locally via Robolectric (no device needed).
 * Tests Laplacian variance on all 6 fixture images to show why
 * it fails on motion blur and why ML Kit OCR is needed.
 *
 * Run:
 *   ./gradlew testDebugUnitTest --tests "*.BlurDetectionBenchmarkTest"
 *
 * Photos:
 *   1 = sharp title deed (Amharic handwritten)     → expected SHARP
 *   2 = blurry keyboard (focus blur)                → expected BLURRY
 *   3 = blurry screen (motion blur)                 → expected BLURRY
 *   4 = sharp keyboard (clear text)                 → expected SHARP
 *   5 = blurry keyboard (extreme motion)            → expected VERY_BLURRY
 *   6 = sharp book page (Indonesian printed text)   → expected SHARP
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class BlurDetectionBenchmarkTest {

    data class PhotoFixture(
        val filename: String,
        val description: String,
        val expectedLabel: String
    )

    private val fixtures = listOf(
        PhotoFixture("blurry-detection-test-1.png", "Title deed (Amharic)", "SHARP"),
        PhotoFixture("blurry-detection-test-2.jpeg", "Keyboard focus blur", "BLURRY"),
        PhotoFixture("blurry-detection-test-3.jpeg", "Screen motion blur", "BLURRY"),
        PhotoFixture("blurry-detection-test-4.jpeg", "Keyboard clear", "SHARP"),
        PhotoFixture("blurry-detection-test-5.jpeg", "Keyboard extreme motion", "VERY_BLURRY"),
        PhotoFixture("blurry-detection-test-6.jpeg", "Book page (Indonesian)", "SHARP"),
    )

    // =========================================================================
    // Main benchmark: Laplacian variance on all fixture photos
    // =========================================================================

    @Test
    fun `benchmark Laplacian variance on all fixture photos`() {
        println("")
        println("╔════════════════════════════════════════════════════════════════════════╗")
        println("║              LAPLACIAN VARIANCE BENCHMARK (real photos)               ║")
        println("╠════════════════════════════════════════════════════════════════════════╣")
        println("║  This shows WHY Laplacian alone is insufficient for motion blur.      ║")
        println("╚════════════════════════════════════════════════════════════════════════╝")
        println("")
        println(
            String.format(
                "%-5s │ %-30s │ %10s │ %12s │ %s",
                "#", "Description", "Laplacian", "Expected", "Correct?"
            )
        )
        println("─".repeat(80))

        var correct = 0
        var total = 0

        for (fixture in fixtures) {
            val bitmap = loadFixturePhoto(fixture.filename)
            if (bitmap == null) {
                println("  SKIP: ${fixture.filename} not found")
                continue
            }

            val variance = computeLaplacianVariance(bitmap)
            bitmap.recycle()

            // Classify using default thresholds
            val laplacianLabel = when {
                variance < 50.0 -> "VERY_BLURRY"
                variance < 100.0 -> "BORDERLINE"
                else -> "SHARP"
            }

            val isCorrect = when (fixture.expectedLabel) {
                "SHARP" -> laplacianLabel == "SHARP"
                "BLURRY" -> laplacianLabel != "SHARP"
                "VERY_BLURRY" -> laplacianLabel == "VERY_BLURRY"
                else -> false
            }

            total++
            if (isCorrect) correct++

            println(
                String.format(
                    "%-5s │ %-30s │ %10.1f │ %-12s │ %s",
                    fixture.filename.substringAfter("test-").substringBefore("."),
                    fixture.description,
                    variance,
                    "${fixture.expectedLabel} → $laplacianLabel",
                    if (isCorrect) "✓" else "✗ WRONG"
                )
            )
        }

        println("─".repeat(80))
        println("Accuracy: $correct/$total (${if (total > 0) correct * 100 / total else 0}%)")
        println("")
        println("NOTE: Laplacian often scores motion-blurred images as SHARP")
        println("because directional streaks register as high-variance edges.")
        println("This is why ML Kit OCR confidence is needed as the primary method.")
        println("")
    }

    // =========================================================================
    // Detailed per-photo analysis
    // =========================================================================

    @Test
    fun `detailed analysis with multiple metrics per photo`() {
        println("")
        println("╔═══════════════════════════════════════════════════════════════════════════════════╗")
        println("║                    DETAILED MULTI-METRIC ANALYSIS                                ║")
        println("╚═══════════════════════════════════════════════════════════════════════════════════╝")
        println("")

        for (fixture in fixtures) {
            val bitmap = loadFixturePhoto(fixture.filename) ?: continue

            val width = bitmap.width
            val height = bitmap.height
            val maxDim = 500
            val scaled = if (width > maxDim || height > maxDim) {
                val scale = maxDim.toDouble() / maxOf(width, height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val gray = toGrayscale(scaled)
            val w = scaled.width
            val h = scaled.height

            val laplacianVar = computeLaplacianVarianceFromGray(gray, w, h)
            val tenengrad = computeTenengrad(gray, w, h)
            val edgeDensity = computeEdgeDensity(gray, w, h)
            val sobelDirectionRatio = computeSobelDirectionRatio(gray, w, h)

            if (scaled != bitmap) scaled.recycle()
            bitmap.recycle()

            println("┌─ ${fixture.description} (${fixture.expectedLabel})")
            println("│  File: ${fixture.filename}")
            println("│  Size: ${width}x${height} → ${w}x${h}")
            println("│")
            println("│  Laplacian variance:    ${String.format("%10.1f", laplacianVar)}")
            println("│  Tenengrad (Sobel mag): ${String.format("%10.1f", tenengrad)}")
            println("│  Edge density:          ${String.format("%10.3f", edgeDensity)} (${String.format("%.1f", edgeDensity * 100)}%)")
            println("│  Sobel direction ratio: ${String.format("%10.2f", sobelDirectionRatio)} (>2.0 = motion blur)")
            println("└─")
            println("")
        }
    }

    // =========================================================================
    // Synthetic baselines (always pass)
    // =========================================================================

    @Test
    fun `uniform gray image has near-zero Laplacian`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val variance = computeLaplacianVariance(bitmap)
        bitmap.recycle()

        println("Uniform gray Laplacian: $variance")
        assert(variance < 1.0) { "Expected < 1.0, got $variance" }
    }

    @Test
    fun `checkerboard has high Laplacian`() {
        val size = 200
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (((x / 4) + (y / 4)) % 2 == 0) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
            }
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        val variance = computeLaplacianVariance(bitmap)
        bitmap.recycle()

        println("Checkerboard Laplacian: $variance")
        assert(variance > 50.0) { "Expected > 50.0, got $variance" }
    }

    // =========================================================================
    // Image processing helpers
    // =========================================================================

    private fun loadFixturePhoto(filename: String): Bitmap? {
        val stream = javaClass.classLoader?.getResourceAsStream(
            "fixtures/blur_test_photos/$filename"
        ) ?: return null
        return BitmapFactory.decodeStream(stream)
    }

    private fun toGrayscale(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            gray[i] = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
        }
        return gray
    }

    private fun computeLaplacianVariance(bitmap: Bitmap): Double {
        val maxDim = 500
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toDouble() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val gray = toGrayscale(scaled)
        val w = scaled.width
        val h = scaled.height
        if (scaled != bitmap) scaled.recycle()

        return computeLaplacianVarianceFromGray(gray, w, h)
    }

    private fun computeLaplacianVarianceFromGray(gray: IntArray, w: Int, h: Int): Double {
        val outputSize = (w - 2) * (h - 2)
        if (outputSize <= 0) return 0.0

        val laplacian = DoubleArray(outputSize)
        var idx = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = gray[y * w + x]
                laplacian[idx++] = (gray[(y - 1) * w + x] +
                    gray[(y + 1) * w + x] +
                    gray[y * w + (x - 1)] +
                    gray[y * w + (x + 1)] -
                    4.0 * c)
            }
        }

        return variance(laplacian)
    }

    private fun computeTenengrad(gray: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var total = 0.0
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -gray[(y - 1) * w + (x - 1)] +
                    gray[(y - 1) * w + (x + 1)] +
                    -2 * gray[y * w + (x - 1)] +
                    2 * gray[y * w + (x + 1)] +
                    -gray[(y + 1) * w + (x - 1)] +
                    gray[(y + 1) * w + (x + 1)]

                val gy = -gray[(y - 1) * w + (x - 1)] +
                    -2 * gray[(y - 1) * w + x] +
                    -gray[(y - 1) * w + (x + 1)] +
                    gray[(y + 1) * w + (x - 1)] +
                    2 * gray[(y + 1) * w + x] +
                    gray[(y + 1) * w + (x + 1)]

                total += sqrt((gx.toDouble() * gx) + (gy.toDouble() * gy))
                count++
            }
        }
        return if (count > 0) total / count else 0.0
    }

    private fun computeEdgeDensity(gray: IntArray, w: Int, h: Int): Double {
        val laplacian = DoubleArray((w - 2) * (h - 2))
        var idx = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = gray[y * w + x]
                laplacian[idx++] = (gray[(y - 1) * w + x] +
                    gray[(y + 1) * w + x] +
                    gray[y * w + (x - 1)] +
                    gray[y * w + (x + 1)] -
                    4.0 * c)
            }
        }
        if (laplacian.isEmpty()) return 0.0

        val stddev = sqrt(variance(laplacian))
        val threshold = stddev * 0.5
        var edgeCount = 0
        for (v in laplacian) {
            if (abs(v) > threshold) edgeCount++
        }
        return edgeCount.toDouble() / laplacian.size
    }

    private fun computeSobelDirectionRatio(gray: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 1.0
        var sumGx = 0.0
        var sumGy = 0.0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -gray[(y - 1) * w + (x - 1)] +
                    gray[(y - 1) * w + (x + 1)] +
                    -2 * gray[y * w + (x - 1)] +
                    2 * gray[y * w + (x + 1)] +
                    -gray[(y + 1) * w + (x - 1)] +
                    gray[(y + 1) * w + (x + 1)]

                val gy = -gray[(y - 1) * w + (x - 1)] +
                    -2 * gray[(y - 1) * w + x] +
                    -gray[(y - 1) * w + (x + 1)] +
                    gray[(y + 1) * w + (x - 1)] +
                    2 * gray[(y + 1) * w + x] +
                    gray[(y + 1) * w + (x + 1)]

                sumGx += gx.toDouble() * gx
                sumGy += gy.toDouble() * gy
            }
        }

        val maxG = maxOf(sumGx, sumGy)
        val minG = minOf(sumGx, sumGy)
        return if (minG > 0) maxG / minG else if (maxG > 0) 10.0 else 1.0
    }

    private fun variance(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val n = values.size.toDouble()
        var sum = 0.0
        var sumSq = 0.0
        for (v in values) {
            sum += v
            sumSq += v * v
        }
        val mean = sum / n
        return (sumSq / n) - (mean * mean)
    }
}
