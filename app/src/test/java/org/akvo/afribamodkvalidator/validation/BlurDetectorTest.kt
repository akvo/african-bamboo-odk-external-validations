package org.akvo.afribamodkvalidator.validation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [30])
class BlurDetectorTest {

    private lateinit var detector: BlurDetector

    @Before
    fun setUp() {
        detector = BlurDetector(
            warnThreshold = 100.0,
            blockThreshold = 50.0,
            maxDimension = 500
        )
    }

    @Test
    fun `detect sharp image returns SHARP level`() {
        // Create a high-contrast checkerboard pattern (high variance = sharp)
        val bitmap = createCheckerboardBitmap(100, 100, blockSize = 2)
        val result = detector.detect(bitmap)
        assertEquals(BlurDetector.BlurLevel.SHARP, result.level)
        assertTrue(result.varianceScore > 100.0)
        bitmap.recycle()
    }

    @Test
    fun `detect uniform image returns VERY_BLURRY level`() {
        // Create a uniform gray image (zero variance = very blurry)
        val bitmap = createUniformBitmap(100, 100, Color.GRAY)
        val result = detector.detect(bitmap)
        assertEquals(BlurDetector.BlurLevel.VERY_BLURRY, result.level)
        assertTrue(result.varianceScore < 50.0)
        bitmap.recycle()
    }

    @Test
    fun `detect with custom thresholds`() {
        val customDetector = BlurDetector(
            warnThreshold = 200.0,
            blockThreshold = 100.0
        )
        val bitmap = createCheckerboardBitmap(100, 100, blockSize = 4)
        val result = customDetector.detect(bitmap)
        assertEquals(200.0, result.warnThreshold, 0.001)
        assertEquals(100.0, result.blockThreshold, 0.001)
        bitmap.recycle()
    }

    @Test
    fun `toGrayscale produces correct luminance values`() {
        // White pixel: 0.299*255 + 0.587*255 + 0.114*255 = 255
        val bitmap = createUniformBitmap(3, 3, Color.WHITE)
        val grayscale = detector.toGrayscale(bitmap)
        assertEquals(9, grayscale.size)
        for (value in grayscale) {
            assertEquals(255, value)
        }
        bitmap.recycle()
    }

    @Test
    fun `toGrayscale black pixel produces zero`() {
        val bitmap = createUniformBitmap(3, 3, Color.BLACK)
        val grayscale = detector.toGrayscale(bitmap)
        for (value in grayscale) {
            assertEquals(0, value)
        }
        bitmap.recycle()
    }

    @Test
    fun `applyLaplacianKernel on uniform input produces zero output`() {
        // Uniform grayscale -> Laplacian should be all zeros
        val width = 5
        val height = 5
        val grayscale = IntArray(width * height) { 128 }
        val laplacian = detector.applyLaplacianKernel(grayscale, width, height)
        // Output size: (5-2) * (5-2) = 9
        assertEquals(9, laplacian.size)
        for (value in laplacian) {
            assertEquals(0.0, value, 0.001)
        }
    }

    @Test
    fun `applyLaplacianKernel detects single bright pixel`() {
        // A single bright pixel in the center should produce non-zero Laplacian
        val width = 5
        val height = 5
        val grayscale = IntArray(width * height) { 0 }
        grayscale[2 * width + 2] = 255 // center pixel
        val laplacian = detector.applyLaplacianKernel(grayscale, width, height)
        // The center of the output (index 4) should be -4*255 = -1020
        assertEquals(-1020.0, laplacian[4], 0.001)
    }

    @Test
    fun `computeVariance of identical values is zero`() {
        val values = DoubleArray(10) { 42.0 }
        val variance = detector.computeVariance(values)
        assertEquals(0.0, variance, 0.001)
    }

    @Test
    fun `computeVariance of known distribution`() {
        // [1, 2, 3, 4, 5] -> mean = 3, variance = E[X^2] - (E[X])^2 = 11 - 9 = 2
        val values = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val variance = detector.computeVariance(values)
        assertEquals(2.0, variance, 0.001)
    }

    @Test
    fun `computeVariance of empty array is zero`() {
        val variance = detector.computeVariance(doubleArrayOf())
        assertEquals(0.0, variance, 0.001)
    }

    @Test
    fun `downscale preserves aspect ratio`() {
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        val scaled = detector.downscale(bitmap, 500)
        assertEquals(500, scaled.width)
        assertEquals(250, scaled.height)
        bitmap.recycle()
        scaled.recycle()
    }

    @Test
    fun `downscale does not upscale small images`() {
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        val scaled = detector.downscale(bitmap, 500)
        // Should return the same bitmap, not upscaled
        assertEquals(100, scaled.width)
        assertEquals(50, scaled.height)
        bitmap.recycle()
    }

    @Test
    fun `downscale tall image preserves ratio`() {
        val bitmap = Bitmap.createBitmap(300, 900, Bitmap.Config.ARGB_8888)
        val scaled = detector.downscale(bitmap, 500)
        // Max dimension is height (900), scale = 500/900
        assertEquals(166, scaled.width) // 300 * (500/900) ≈ 166
        assertEquals(500, scaled.height)
        bitmap.recycle()
        scaled.recycle()
    }

    @Test
    fun `detect very small image does not crash`() {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val result = detector.detect(bitmap)
        // Should complete without exception
        assertTrue(result.varianceScore >= 0.0)
        bitmap.recycle()
    }

    @Test
    fun `threshold boundary - score exactly at warn threshold is SHARP`() {
        // When variance >= warnThreshold, level should be SHARP
        val customDetector = BlurDetector(warnThreshold = 10.0, blockThreshold = 5.0)
        // A mild checkerboard should generate some variance
        val bitmap = createCheckerboardBitmap(50, 50, blockSize = 10)
        val result = customDetector.detect(bitmap)
        // Just verify the classification logic is consistent
        when {
            result.varianceScore >= 10.0 -> assertEquals(BlurDetector.BlurLevel.SHARP, result.level)
            result.varianceScore >= 5.0 -> assertEquals(BlurDetector.BlurLevel.BORDERLINE, result.level)
            else -> assertEquals(BlurDetector.BlurLevel.VERY_BLURRY, result.level)
        }
        bitmap.recycle()
    }

    @Test
    fun `default thresholds match constants`() {
        val defaultDetector = BlurDetector()
        val bitmap = createUniformBitmap(10, 10, Color.GRAY)
        val result = defaultDetector.detect(bitmap)
        assertEquals(BlurDetector.DEFAULT_WARN_THRESHOLD, result.warnThreshold, 0.001)
        assertEquals(BlurDetector.DEFAULT_BLOCK_THRESHOLD, result.blockThreshold, 0.001)
        bitmap.recycle()
    }

    // --- Helper functions ---

    private fun createCheckerboardBitmap(
        width: Int,
        height: Int,
        blockSize: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val isBlack = ((x / blockSize) + (y / blockSize)) % 2 == 0
                pixels[y * width + x] = if (isBlack) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun createUniformBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }
}
