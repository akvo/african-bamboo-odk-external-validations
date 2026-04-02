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
        detector = BlurDetector()
    }

    // --- Laplacian variance tests ---

    @Test
    fun `laplacian on uniform image returns near zero`() {
        val bitmap = createUniformBitmap(100, 100, Color.GRAY)
        val variance = detector.computeLaplacianVariance(bitmap)
        bitmap.recycle()

        assertTrue("Uniform image variance should be < 1.0, got $variance", variance < 1.0)
    }

    @Test
    fun `laplacian on checkerboard returns high variance`() {
        val bitmap = createCheckerboardBitmap(100, 100, blockSize = 4)
        val variance = detector.computeLaplacianVariance(bitmap)
        bitmap.recycle()

        assertTrue("Checkerboard variance should be > 50, got $variance", variance > 50.0)
    }

    @Test
    fun `laplacian on very small image does not crash`() {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val variance = detector.computeLaplacianVariance(bitmap)
        bitmap.recycle()

        assertTrue(variance >= 0.0)
    }

    // --- Downscale tests ---

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
        assertEquals(100, scaled.width)
        assertEquals(50, scaled.height)
        bitmap.recycle()
    }

    // --- Grayscale tests ---

    @Test
    fun `toGrayscale white produces 255`() {
        val bitmap = createUniformBitmap(3, 3, Color.WHITE)
        val gray = detector.toGrayscale(bitmap)
        bitmap.recycle()

        for (v in gray) assertEquals(255, v)
    }

    @Test
    fun `toGrayscale black produces 0`() {
        val bitmap = createUniformBitmap(3, 3, Color.BLACK)
        val gray = detector.toGrayscale(bitmap)
        bitmap.recycle()

        for (v in gray) assertEquals(0, v)
    }

    // --- Constants tests ---

    @Test
    fun `default thresholds are set correctly`() {
        assertEquals(0.65, BlurDetector.DEFAULT_OCR_WARN, 0.001)
        assertEquals(0.35, BlurDetector.DEFAULT_OCR_BLOCK, 0.001)
        assertEquals(100.0, BlurDetector.DEFAULT_LAP_WARN, 0.001)
        assertEquals(50.0, BlurDetector.DEFAULT_LAP_BLOCK, 0.001)
        assertEquals(5, BlurDetector.MIN_ELEMENTS_FOR_OCR)
    }

    // --- Helpers ---

    private fun createCheckerboardBitmap(width: Int, height: Int, blockSize: Int): Bitmap {
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
