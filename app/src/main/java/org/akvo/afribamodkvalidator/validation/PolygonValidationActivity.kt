package org.akvo.afribamodkvalidator.validation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class PolygonValidationActivity : AppCompatActivity() {

    private val validator = PolygonValidator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get input from ODK Intent - expects "shape" extra from XLSForm
        val polygonData = intent.getStringExtra("shape")

        if (polygonData.isNullOrBlank()) {
            showErrorAndBlock("No polygon data received from form.", null)
            return
        }

        when (val result = validator.validate(polygonData)) {
            is ValidationResult.Success -> {
                returnSuccess(polygonData)
            }
            is ValidationResult.Error -> {
                showErrorAndBlock(result.message, polygonData)
            }
        }
    }

    private fun showErrorAndBlock(message: String, data: String?) {
        AlertDialog.Builder(this)
            .setTitle("Validation Failed")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                if (!data.isNullOrEmpty()) {
                    // Data available: set RESULT_OK with reset (null) value
                    val resultIntent = Intent().apply {
                        putExtra("value", null as String?)
                    }
                    setResult(RESULT_OK, resultIntent)
                } else {
                    // No data: set RESULT_CANCELED without intent data
                    setResult(RESULT_CANCELED)
                }
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun returnSuccess(data: String) {
        Toast.makeText(this, "Polygon validated successfully", Toast.LENGTH_SHORT).show()
        val resultIntent = Intent().apply {
            putExtra("value", data)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
