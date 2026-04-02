package org.akvo.afribamodkvalidator.validation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akvo.afribamodkvalidator.data.settings.ValidationSettingsDataStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Custom camera app for ODK Collect with blur validation.
 *
 * Captures a photo, validates text readability via ML Kit OCR
 * (with Laplacian fallback), stamps a quality watermark,
 * and returns the image to ODK Collect.
 *
 * XLSForm: type=image, parameters=app=org.akvo.afribamodkvalidator (leave appearance blank; do not use appearance=new)
 */
@AndroidEntryPoint
class BlurValidationActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsDataStore: ValidationSettingsDataStore

    private var outputUri: Uri? = null
    private var currentPhotoPath: String? = null
    private var currentPhotoUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            Log.d(TAG, "Photo captured: $currentPhotoPath")
            validateAndStamp(currentPhotoPath!!)
        } else {
            Log.d(TAG, "Camera cancelled or failed")
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.let {
            currentPhotoPath = it.getString(STATE_PHOTO_PATH)
            currentPhotoUri = getParcelableCompat(it, STATE_PHOTO_URI)
            outputUri = getParcelableCompat(it, STATE_OUTPUT_URI)
        }

        Log.d(TAG, "Intent action: ${intent.action}")
        intent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                Log.d(TAG, "Intent extra: key='$key', value='${bundle.getString(key)}'")
            }
        }

        if (outputUri == null) {
            outputUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
            }
        }
        Log.d(TAG, "Output URI: $outputUri")

        if (savedInstanceState == null) {
            launchCamera()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_PATH, currentPhotoPath)
        outState.putParcelable(STATE_PHOTO_URI, currentPhotoUri)
        outState.putParcelable(STATE_OUTPUT_URI, outputUri)
    }

    private fun launchCamera() {
        val photoFile = createImageFile()
        if (photoFile == null) {
            showDialog("Error", "Could not create image file.")
            return
        }

        currentPhotoPath = photoFile.absolutePath
        currentPhotoUri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", photoFile
        )

        Log.d(TAG, "Launching camera, saving to: $currentPhotoPath")
        takePictureLauncher.launch(currentPhotoUri!!)
    }

    private fun createImageFile(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("IMG_$timestamp", ".jpg", storageDir)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create image file", e)
            null
        }
    }

    private fun validateAndStamp(filePath: String) {
        lifecycleScope.launch {
            try {
                // Load bitmap and apply EXIF rotation on IO thread
                val bitmap = withContext(Dispatchers.IO) {
                    loadAndRotateBitmap(filePath)
                }
                if (bitmap == null) {
                    showDialog("Invalid Image", "Could not load the captured image.")
                    return@launch
                }

                val settings = settingsDataStore.getSettings()
                val detector = BlurDetector()

                // Run detection on Default dispatcher (CPU-bound work)
                val result = withContext(Dispatchers.Default) {
                    detector.detect(
                        bitmap = bitmap,
                        ocrWarnThreshold = settings.ocrWarnThreshold,
                        ocrBlockThreshold = settings.ocrBlockThreshold,
                        lapWarnThreshold = settings.laplacianWarnThreshold,
                        lapBlockThreshold = settings.laplacianBlockThreshold
                    )
                }
                bitmap.recycle()

                Log.d(
                    TAG,
                    "Score: ${"%.3f".format(result.score)}, " +
                        "Method: ${result.method}, " +
                        "Elements: ${result.elementCount}, " +
                        "Level: ${result.level}, " +
                        "Text: '${result.detectedText.take(40)}'"
                )

                // Stamp watermark on IO thread
                withContext(Dispatchers.IO) {
                    ImageWatermark.stamp(filePath, result)
                }

                when (result.level) {
                    BlurDetector.BlurLevel.SHARP -> returnImageToOdk(filePath)
                    BlurDetector.BlurLevel.BORDERLINE -> showWarningDialog(filePath, result)
                    BlurDetector.BlurLevel.VERY_BLURRY -> showBlockDialog(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Blur detection failed", e)
                showDialog("Error", "Failed to analyze image quality. Please try again.")
            }
        }
    }

    /**
     * Load a JPEG and apply EXIF rotation so the bitmap is upright.
     * Many camera apps store rotation in EXIF rather than rotating pixels.
     */
    private fun loadAndRotateBitmap(filePath: String): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(filePath) ?: return null

        val rotation = try {
            val exif = ExifInterface(filePath)
            when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation", e)
            0f
        }

        if (rotation == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()

        // Save the rotated bitmap back so watermark and ODK get correct orientation
        try {
            File(filePath).outputStream().use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save rotated bitmap", e)
        }

        return rotated
    }

    private fun showWarningDialog(filePath: String, result: BlurDetector.BlurResult) {
        val qualityPercent = if (result.method == BlurDetector.METHOD_OCR) {
            "${(result.score * 100).toInt()}%"
        } else {
            "%.0f".format(result.score)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Image Quality Warning")
            .setMessage(
                "This image may be hard to read (quality: $qualityPercent). " +
                    "Consider retaking the photo."
            )
            .setCancelable(false)
            .setPositiveButton("Use Anyway") { dlg, _ ->
                dlg.dismiss()
                returnImageToOdk(filePath)
            }
            .setNegativeButton("Reject") { dlg, _ ->
                dlg.dismiss()
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()

        applyButtonColors(dialog)
    }

    private fun showBlockDialog(result: BlurDetector.BlurResult) {
        val qualityPercent = if (result.method == BlurDetector.METHOD_OCR) {
            "${(result.score * 100).toInt()}%"
        } else {
            "%.0f".format(result.score)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Image Too Blurry")
            .setMessage(
                "This image is too blurry to read (quality: $qualityPercent). " +
                    "Please retake the photo."
            )
            .setCancelable(false)
            .setPositiveButton("OK") { dlg, _ ->
                dlg.dismiss()
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()

        applyButtonColors(dialog)
    }

    private fun showDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dlg, _ ->
                dlg.dismiss()
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()

        applyButtonColors(dialog)
    }

    private fun applyButtonColors(dialog: AlertDialog) {
        val primaryColor = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            getColor(android.R.color.holo_blue_dark)
        )
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primaryColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(primaryColor)
    }

    private fun returnImageToOdk(filePath: String) {
        Toast.makeText(this, "Image quality check passed", Toast.LENGTH_SHORT).show()

        val targetUri = outputUri
        if (targetUri != null) {
            try {
                contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                    File(filePath).inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Image copied to ODK output URI: $targetUri")
                setResult(RESULT_OK)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy image to ODK URI", e)
                setResult(RESULT_CANCELED)
            }
        } else {
            Log.d(TAG, "No output URI, returning file path")
            val resultIntent = Intent().apply {
                putExtra("value", filePath)
            }
            setResult(RESULT_OK, resultIntent)
        }
        finish()
    }

    private inline fun <reified T : android.os.Parcelable> getParcelableCompat(
        bundle: Bundle,
        key: String
    ): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable(key)
        }
    }

    companion object {
        private const val TAG = "BlurValidationActivity"
        private const val STATE_PHOTO_PATH = "photo_path"
        private const val STATE_PHOTO_URI = "photo_uri"
        private const val STATE_OUTPUT_URI = "output_uri"
    }
}
