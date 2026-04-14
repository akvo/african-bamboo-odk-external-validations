package org.akvo.afribamodkvalidator.validation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark test for blur detection using real photos from fixtures/.
 *
 * Runs on a real device/emulator to compare ML Kit OCR confidence
 * vs Laplacian variance on actual images.
 *
 * Test photos in: app/src/androidTest/assets/blur_test_photos/
 *
 * Naming convention:
 *   sharp_*    — clearly readable text (expected: SHARP)
 *   blurry_*   — motion/focus blur, unreadable (expected: BLURRY)
 *   borderline_* — somewhat readable (expected: BORDERLINE)
 *
 * Run:
 *   ./gradlew connectedAndroidTest --tests "*.BlurDetectionBenchmarkTest"
 *
 * Check logcat:
 *   adb logcat -s BlurBenchmark
 */
@RunWith(AndroidJUnit4::class)
class BlurDetectionBenchmarkTest {

    // =========================================================================
    // Side-by-side benchmark: OCR vs Laplacian on all photos
    // =========================================================================

    @Test
    fun benchmark_side_by_side_all_photos(): Unit = runBlocking {
        val photos = loadTestPhotos()
        assumeTrue(
            "No test photos in assets/blur_test_photos/ (fixtures are gitignored). " +
                "Copy from fixtures/ to run this benchmark manually.",
            photos.isNotEmpty()
        )

        Log.d(TAG, "")
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║                    BLUR DETECTION BENCHMARK: OCR vs LAPLACIAN                    ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════════════════════════════╝")
        Log.d(TAG, "")
        Log.d(TAG, String.format(
            "%-38s │ %6s │ %5s │ %5s │ %10s │ %-8s │ %s",
            "Photo", "OCR", "Blks", "Elems", "Laplacian", "Expected", "OCR Text (first 40 chars)"
        ))
        Log.d(TAG, "─".repeat(120))

        for ((filename, bitmap) in photos) {
            val ocrResult = runOcrAnalysis(bitmap)
            val laplacian = computeLaplacianVariance(bitmap)
            bitmap.recycle()

            val expected = classifyExpected(filename)

            Log.d(TAG, String.format(
                "%-38s │ %5.3f │ %5d │ %5d │ %10.1f │ %-8s │ %.40s",
                filename.take(38),
                ocrResult.avgConfidence,
                ocrResult.textBlockCount,
                ocrResult.elementCount,
                laplacian,
                expected,
                ocrResult.detectedText.replace("\n", " ").take(40)
            ))
        }

        Log.d(TAG, "─".repeat(120))
        Log.d(TAG, "")
        Log.d(TAG, "LEGEND: OCR = avg element confidence (0.0-1.0), Blks = text blocks, Elems = text elements")
        Log.d(TAG, "")
    }

    // =========================================================================
    // Individual photo assertions (RED phase — expected to reveal thresholds)
    // =========================================================================

    @Test
    fun sharp_title_deed_should_have_high_ocr_confidence(): Unit = runBlocking {
        val bitmap = loadPhoto("sharp_title_deed_amharic.png") ?: run { assumeTrue("Fixture not found (gitignored)", false); return@runBlocking }
        val result = runOcrAnalysis(bitmap)
        bitmap.recycle()

        logPhotoResult("sharp_title_deed_amharic", result)

        assertTrue(
            "Title deed: expected text elements > 0, got ${result.elementCount}",
            result.elementCount > 0
        )
        // Logging the score — we'll set the threshold AFTER seeing real scores
        Log.d(TAG, ">>> SHARP title deed OCR confidence: ${result.avgConfidence}")
    }

    @Test
    fun sharp_keyboard_should_have_high_ocr_confidence(): Unit = runBlocking {
        val bitmap = loadPhoto("sharp_keyboard_clear.jpeg") ?: run { assumeTrue("Fixture not found (gitignored)", false); return@runBlocking }
        val result = runOcrAnalysis(bitmap)
        bitmap.recycle()

        logPhotoResult("sharp_keyboard_clear", result)

        assertTrue(
            "Sharp keyboard: expected text elements > 0, got ${result.elementCount}",
            result.elementCount > 0
        )
        Log.d(TAG, ">>> SHARP keyboard OCR confidence: ${result.avgConfidence}")
    }

    @Test
    fun sharp_book_page_should_have_high_ocr_confidence(): Unit = runBlocking {
        val bitmap = loadPhoto("sharp_book_page_indonesian.jpeg") ?: run { assumeTrue("Fixture not found (gitignored)", false); return@runBlocking }
        val result = runOcrAnalysis(bitmap)
        bitmap.recycle()

        logPhotoResult("sharp_book_page_indonesian", result)

        assertTrue(
            "Book page: expected many text elements, got ${result.elementCount}",
            result.elementCount > 5
        )
        Log.d(TAG, ">>> SHARP book page OCR confidence: ${result.avgConfidence}")
    }

    @Test
    fun blurry_keyboard_focus_should_have_low_ocr_confidence(): Unit = runBlocking {
        val bitmap = loadPhoto("blurry_keyboard_focus.jpeg") ?: run { assumeTrue("Fixture not found (gitignored)", false); return@runBlocking }
        val result = runOcrAnalysis(bitmap)
        bitmap.recycle()

        logPhotoResult("blurry_keyboard_focus", result)

        // Focus blur should reduce OCR confidence or element count
        Log.d(TAG, ">>> BLURRY focus: OCR confidence=${result.avgConfidence}, elements=${result.elementCount}")
    }

    @Test
    fun blurry_screen_motion_should_have_low_ocr_confidence(): Unit = runBlocking {
        val bitmap = loadPhoto("blurry_screen_motion.jpeg") ?: run { assumeTrue("Fixture not found (gitignored)", false); return@runBlocking }
        val result = runOcrAnalysis(bitmap)
        bitmap.recycle()

        logPhotoResult("blurry_screen_motion", result)

        Log.d(TAG, ">>> BLURRY motion: OCR confidence=${result.avgConfidence}, elements=${result.elementCount}")
    }

    @Test
    fun blurry_extreme_motion_should_have_very_low_ocr_confidence(): Unit = runBlocking {
        val bitmap = loadPhoto("blurry_keyboard_extreme_motion.jpeg") ?: run { assumeTrue("Fixture not found (gitignored)", false); return@runBlocking }
        val result = runOcrAnalysis(bitmap)
        bitmap.recycle()

        logPhotoResult("blurry_keyboard_extreme_motion", result)

        Log.d(TAG, ">>> VERY BLURRY extreme motion: OCR confidence=${result.avgConfidence}, elements=${result.elementCount}")
    }

    // =========================================================================
    // Laplacian-only tests (to show why it fails on motion blur)
    // =========================================================================

    @Test
    fun laplacian_comparison_sharp_vs_blurry() {
        val photos = loadTestPhotos()
        assumeTrue("No test photos (gitignored)", photos.isNotEmpty())

        Log.d(TAG, "")
        Log.d(TAG, "╔═══════════════════════════════════════════════════════════╗")
        Log.d(TAG, "║         LAPLACIAN VARIANCE (fallback method)             ║")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════════╝")
        Log.d(TAG, "")
        Log.d(TAG, String.format("%-38s │ %10s │ %s", "Photo", "Variance", "Expected"))
        Log.d(TAG, "─".repeat(65))

        for ((filename, bitmap) in photos) {
            val variance = computeLaplacianVariance(bitmap)
            bitmap.recycle()

            Log.d(TAG, String.format(
                "%-38s │ %10.1f │ %s",
                filename.take(38),
                variance,
                classifyExpected(filename)
            ))
        }

        Log.d(TAG, "─".repeat(65))
        Log.d(TAG, "")
        Log.d(TAG, "NOTE: Laplacian often gives HIGH scores to motion-blurred images.")
        Log.d(TAG, "This is WHY we need ML Kit OCR confidence as the primary method.")
        Log.d(TAG, "")
    }

    // =========================================================================
    // Synthetic baseline tests (always pass, no photos needed)
    // =========================================================================

    @Test
    fun ocr_uniform_gray_returns_zero_elements(): Unit = runBlocking {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)

        val result = runOcrAnalysis(bitmap)
        bitmap.recycle()

        Log.d(TAG, "Uniform gray: confidence=${result.avgConfidence}, elements=${result.elementCount}")
        assertTrue("Uniform image should have 0 elements", result.elementCount == 0)
    }

    @Test
    fun laplacian_uniform_returns_near_zero() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)

        val variance = computeLaplacianVariance(bitmap)
        bitmap.recycle()

        Log.d(TAG, "Uniform gray Laplacian: $variance")
        assertTrue("Uniform image variance should be < 1.0, got $variance", variance < 1.0)
    }

    @Test
    fun laplacian_checkerboard_returns_high_variance() {
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

        Log.d(TAG, "Checkerboard Laplacian: $variance")
        assertTrue("Checkerboard should have high variance, got $variance", variance > 50.0)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    data class OcrResult(
        val avgConfidence: Double,
        val textBlockCount: Int,
        val elementCount: Int,
        val detectedText: String
    )

    private suspend fun runOcrAnalysis(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        return try {
            val visionText: Text = recognizer.process(image).await()

            val allElements = mutableListOf<Text.Element>()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        allElements.add(element)
                    }
                }
            }

            val confidences = allElements.mapNotNull { element -> element.confidence }
            val avgConfidence = if (confidences.isNotEmpty()) confidences.average() else 0.0

            OcrResult(
                avgConfidence = avgConfidence,
                textBlockCount = visionText.textBlocks.size,
                elementCount = allElements.size,
                detectedText = visionText.text.take(200)
            )
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            OcrResult(0.0, 0, 0, "ERROR: ${e.message}")
        } finally {
            recognizer.close()
        }
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

        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled != bitmap) scaled.recycle()

        val gray = IntArray(width * height)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])
            gray[i] = ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
        }

        val outputSize = (width - 2) * (height - 2)
        if (outputSize <= 0) return 0.0

        val laplacian = DoubleArray(outputSize)
        var idx = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val c = gray[y * width + x]
                laplacian[idx++] = (gray[(y - 1) * width + x] +
                    gray[(y + 1) * width + x] +
                    gray[y * width + (x - 1)] +
                    gray[y * width + (x + 1)] -
                    4.0 * c)
            }
        }

        val n = laplacian.size.toDouble()
        var sum = 0.0
        var sumSq = 0.0
        for (v in laplacian) {
            sum += v
            sumSq += v * v
        }
        val mean = sum / n
        return (sumSq / n) - (mean * mean)
    }

    private fun loadTestPhotos(): List<Pair<String, Bitmap>> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val photoDir = "blur_test_photos"
        val filenames = try {
            assets.list(photoDir)?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return filenames.mapNotNull { filename ->
            val bitmap = assets.open("$photoDir/$filename").use {
                BitmapFactory.decodeStream(it)
            }
            if (bitmap != null) filename to bitmap else null
        }
    }

    private fun loadPhoto(filename: String): Bitmap? {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return try {
            assets.open("blur_test_photos/$filename").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Photo not found: $filename")
            null
        }
    }

    private fun logPhotoResult(name: String, result: OcrResult) {
        Log.d(TAG, "[$name] confidence=${result.avgConfidence}, " +
            "blocks=${result.textBlockCount}, elements=${result.elementCount}, " +
            "text='${result.detectedText.take(60).replace("\n", " ")}'")
    }

    private fun classifyExpected(filename: String): String = when {
        filename.startsWith("sharp") -> "SHARP"
        filename.startsWith("blurry") -> "BLURRY"
        filename.startsWith("borderline") -> "BORDER"
        else -> "???"
    }

    companion object {
        private const val TAG = "BlurBenchmark"
    }
}
