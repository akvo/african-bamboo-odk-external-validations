package com.akvo.externalodk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class RegionValidationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. Get region code from Kobo
        val regionCode = intent.getStringExtra("region_code")

        // 2. Get valid region from SharedPreferences
        val sharedPreferences = getSharedPreferences("region_prefs", MODE_PRIVATE)
        val validRegion = sharedPreferences.getString("valid_region", "")

        Log.d("RegionValidationActivity", "regionCode: $regionCode, validRegion: $validRegion")
        val isValid = regionCode == validRegion

        // 3. Return Result
        val resultIntent = Intent()
        if (isValid) {
            resultIntent.putExtra("value", "valid: Success!")
        } else {
            resultIntent.putExtra("value", "invalid: This is an example of an error message.")
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}