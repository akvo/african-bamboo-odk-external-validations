package org.akvo.afribamodkvalidator.validation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akvo.afribamodkvalidator.data.settings.ValidationSettingsDataStore
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class BlurValidationActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsDataStore: ValidationSettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra(EXTRA_VALUE)

        if (filePath.isNullOrBlank()) {
            showBlockDialog(
                title = "No Image",
                message = "No image file path received from form.",
                isCanceled = true
            )
            return
        }

        if (!isAllowedPath(filePath)) {
            showBlockDialog(
                title = "Invalid Path",
                message = "The image path is not allowed.",
                isCanceled = true
            )
            return
        }

        val bitmap = BitmapFactory.decodeFile(filePath)
        if (bitmap == null) {
            showBlockDialog(
                title = "Invalid Image",
                message = "Could not load the image file. It may be corrupted or missing.",
                isCanceled = true
            )
            return
        }

        processImage(bitmap, filePath)
    }

    private fun processImage(bitmap: Bitmap, filePath: String) {
        lifecycleScope.launch {
            try {
                val settings = settingsDataStore.getSettings()
                val detector = BlurDetector(
                    warnThreshold = settings.blurWarnThreshold,
                    blockThreshold = settings.blurBlockThreshold,
                    maxDimension = settings.maxDimension
                )

                val result = withContext(Dispatchers.Default) {
                    detector.detect(bitmap)
                }

                Log.d(TAG, "Blur score: ${result.varianceScore}, level: ${result.level}")

                when (result.level) {
                    BlurDetector.BlurLevel.SHARP -> {
                        returnSuccess(filePath)
                    }
                    BlurDetector.BlurLevel.BORDERLINE -> {
                        showWarningDialog(filePath, result.varianceScore)
                    }
                    BlurDetector.BlurLevel.VERY_BLURRY -> {
                        showBlockDialog(
                            title = "Image Too Blurry",
                            message = "This image is too blurry to read " +
                                "(score: ${"%.1f".format(result.varianceScore)}). " +
                                "Please retake the photo."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Blur detection failed", e)
                showBlockDialog(
                    title = "Error",
                    message = "Failed to analyze image quality. Please try again."
                )
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun isAllowedPath(filePath: String): Boolean {
        return try {
            val resolved = File(filePath).canonicalPath
            val allowedPrefixes = listOfNotNull(
                Environment.getExternalStorageDirectory().canonicalPath,
                getExternalFilesDir(null)?.canonicalPath
            )
            allowedPrefixes.any { resolved.startsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    private fun showWarningDialog(filePath: String, score: Double) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Image Quality Warning")
            .setMessage(
                "This image appears blurry (score: ${"%.1f".format(score)}). " +
                    "Text may be unreadable. Consider retaking the photo."
            )
            .setCancelable(false)
            .setPositiveButton("Retake Photo") { dlg, _ ->
                dlg.dismiss()
                returnReject()
            }
            .setNegativeButton("Use Anyway") { dlg, _ ->
                dlg.dismiss()
                returnSuccess(filePath)
            }
            .show()

        applyButtonColors(dialog)
    }

    private fun showBlockDialog(
        title: String,
        message: String,
        isCanceled: Boolean = false
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dlg, _ ->
                dlg.dismiss()
                if (isCanceled) {
                    setResult(RESULT_CANCELED)
                    finish()
                } else {
                    returnReject()
                }
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

    private fun returnSuccess(filePath: String) {
        Toast.makeText(this, "Image quality check passed", Toast.LENGTH_SHORT).show()
        val resultIntent = Intent().apply {
            putExtra(EXTRA_VALUE, filePath)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun returnReject() {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_VALUE, null as String?)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val TAG = "BlurValidationActivity"
        const val EXTRA_VALUE = "value"
    }
}
