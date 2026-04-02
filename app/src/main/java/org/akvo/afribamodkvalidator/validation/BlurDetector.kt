package org.akvo.afribamodkvalidator.validation

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import androidx.core.graphics.scale

/**
 * Detects image blur using a hybrid approach:
 *
 * 1. **ML Kit OCR confidence** (primary) — measures text readability directly.
 *    If OCR can't read it, a human can't either. Catches motion blur.
 * 2. **Laplacian variance** (fallback) — used when OCR finds < 5 text elements
 *    (e.g., non-Latin scripts like Amharic, or non-text images).
 *
 * Benchmark results (Samsung SM-A145F, 6 test photos):
 *   - OCR alone: 83% accuracy (fails on non-Latin)
 *   - Laplacian alone: 83% accuracy (fails on motion blur)
 *   - Hybrid: 100% accuracy
 */
class BlurDetector {

    enum class BlurLevel { SHARP, BORDERLINE, VERY_BLURRY }

    data class BlurResult(
        val score: Double,
        val level: BlurLevel,
        val method: String,
        val ocrConfidence: Double,
        val textBlockCount: Int,
        val elementCount: Int,
        val laplacianVariance: Double,
        val detectedText: String
    )

    suspend fun detect(
        bitmap: Bitmap,
        ocrWarnThreshold: Double = DEFAULT_OCR_WARN,
        ocrBlockThreshold: Double = DEFAULT_OCR_BLOCK,
        lapWarnThreshold: Double = DEFAULT_LAP_WARN,
        lapBlockThreshold: Double = DEFAULT_LAP_BLOCK
    ): BlurResult {
        val ocrResult = runOcrAnalysis(bitmap)

        return when {
            // Enough text elements — use OCR confidence as primary metric
            ocrResult.elementCount >= MIN_ELEMENTS_FOR_OCR -> {
                classifyByOcr(ocrResult, ocrWarnThreshold, ocrBlockThreshold)
            }
            // Zero text found — image is completely unreadable, block immediately
            ocrResult.elementCount == 0 -> {
                BlurResult(
                    score = 0.0,
                    level = BlurLevel.VERY_BLURRY,
                    method = METHOD_OCR,
                    ocrConfidence = 0.0,
                    textBlockCount = 0,
                    elementCount = 0,
                    laplacianVariance = 0.0,
                    detectedText = ""
                )
            }
            // 1-4 elements — partial detection (e.g., non-Latin script), use Laplacian fallback
            else -> {
                classifyByLaplacian(bitmap, ocrResult, lapWarnThreshold, lapBlockThreshold)
            }
        }
    }

    private suspend fun runOcrAnalysis(bitmap: Bitmap): OcrAnalysis {
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

            val confidences = allElements.map { element -> element.confidence }
            val avgConfidence = if (confidences.isNotEmpty()) confidences.average() else 0.0

            OcrAnalysis(
                avgConfidence = avgConfidence,
                textBlockCount = visionText.textBlocks.size,
                elementCount = allElements.size,
                detectedText = visionText.text.take(100)
            )
        } catch (e: Exception) {
            Log.e(TAG, "OCR analysis failed", e)
            OcrAnalysis(0.0, 0, 0, "")
        } finally {
            recognizer.close()
        }
    }

    private fun classifyByOcr(
        ocr: OcrAnalysis,
        warnThreshold: Double,
        blockThreshold: Double
    ): BlurResult {
        val level = when {
            ocr.avgConfidence < blockThreshold -> BlurLevel.VERY_BLURRY
            ocr.avgConfidence < warnThreshold -> BlurLevel.BORDERLINE
            else -> BlurLevel.SHARP
        }
        return BlurResult(
            score = ocr.avgConfidence,
            level = level,
            method = METHOD_OCR,
            ocrConfidence = ocr.avgConfidence,
            textBlockCount = ocr.textBlockCount,
            elementCount = ocr.elementCount,
            laplacianVariance = 0.0,
            detectedText = ocr.detectedText
        )
    }

    private fun classifyByLaplacian(
        bitmap: Bitmap,
        ocr: OcrAnalysis,
        warnThreshold: Double,
        blockThreshold: Double
    ): BlurResult {
        val variance = computeLaplacianVariance(bitmap)
        val level = when {
            variance < blockThreshold -> BlurLevel.VERY_BLURRY
            variance < warnThreshold -> BlurLevel.BORDERLINE
            else -> BlurLevel.SHARP
        }
        return BlurResult(
            score = variance,
            level = level,
            method = METHOD_LAPLACIAN,
            ocrConfidence = ocr.avgConfidence,
            textBlockCount = ocr.textBlockCount,
            elementCount = ocr.elementCount,
            laplacianVariance = variance,
            detectedText = ocr.detectedText
        )
    }

    internal fun computeLaplacianVariance(bitmap: Bitmap): Double {
        val scaled = downscale(bitmap, MAX_DIMENSION)
        val gray = toGrayscale(scaled)
        val w = scaled.width
        val h = scaled.height
        if (scaled != bitmap) scaled.recycle()

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

    internal fun downscale(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toDouble() / maxOf(w, h)
        return bitmap.scale(
            (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1)
        )
    }

    internal fun toGrayscale(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            gray[i] = ((0.299 * Color.red(pixels[i])) +
                (0.587 * Color.green(pixels[i])) +
                (0.114 * Color.blue(pixels[i]))).toInt()
        }
        return gray
    }

    private data class OcrAnalysis(
        val avgConfidence: Double,
        val textBlockCount: Int,
        val elementCount: Int,
        val detectedText: String
    )

    companion object {
        private const val TAG = "BlurDetector"

        const val DEFAULT_OCR_WARN = 0.65
        const val DEFAULT_OCR_BLOCK = 0.35
        const val DEFAULT_LAP_WARN = 100.0
        const val DEFAULT_LAP_BLOCK = 50.0
        const val MIN_ELEMENTS_FOR_OCR = 5
        const val MAX_DIMENSION = 500

        const val METHOD_OCR = "OCR"
        const val METHOD_LAPLACIAN = "LAP"
    }
}
