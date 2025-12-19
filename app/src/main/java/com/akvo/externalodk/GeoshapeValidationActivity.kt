package com.akvo.externalodk

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.turf.TurfJoins
import java.io.IOException

class GeoshapeValidationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Minimal UI
        val textView = TextView(this)
        textView.text = "Validating geoshape..."
        textView.setPadding(48, 48, 48, 48)
        setContentView(textView)

        val shapeExtra = intent.getStringExtra("shape")
        // debug shapeExtra
        Log.d("GeoshapeValidationActivity", "shapeExtra: $shapeExtra")

        if (shapeExtra.isNullOrBlank()) {
            finishWithResult("invalid input: missing 'shape' extra")
            return
        }

        try {
            val polygon = loadPolygonFromSharedPreferences()
            if (polygon == null) {
                finishWithResult("invalid input: could not load polygon from configuration")
                return
            }

            val points = parseShapeString(shapeExtra)
            if (points.isEmpty()) {
                finishWithResult("invalid input: no vertices found")
                return
            }

            for ((index, point) in points.withIndex()) {
                if (!TurfJoins.inside(point, polygon)) {
                    finishWithResult("invalid: vertex ${index + 1} outside polygon")
                    return
                }
            }

            finishWithResult("valid")
        } catch (e: Exception) {
            finishWithResult("invalid input: ${e.message ?: "parse error"}")
        }
    }

    private fun loadPolygonFromSharedPreferences(): Polygon? {
        return try {
            val sharedPreferences = getSharedPreferences("region_prefs", MODE_PRIVATE)
            val json = sharedPreferences.getString("geojson_polygon", null)
            
            // Fallback to assets if no GeoJSON in SharedPreferences
            val geoJsonContent = json ?: assets.open("polygon.json").bufferedReader().use { it.readText() }
            
            val featureCollection = FeatureCollection.fromJson(geoJsonContent)
            val feature = featureCollection.features()?.firstOrNull()
            feature?.geometry() as? Polygon
        } catch (e: IOException) {
            null
        }
    }

    private fun parseShapeString(shape: String): List<Point> {
        // ODK format: 'lat lon alt acc;lat lon alt acc;...'
        return shape.split(";").filter { it.isNotBlank() }.map { vertex ->
            val parts = vertex.trim().split(Regex("\\s+"))
            if (parts.size < 2) throw Exception("malformed vertex '$vertex'")
            
            val lat = parts[0].toDoubleOrNull() ?: throw Exception("invalid lat '${parts[0]}'")
            val lon = parts[1].toDoubleOrNull() ?: throw Exception("invalid lon '${parts[1]}'")
            
            // Mapbox Turf uses Lng, Lat order
            Point.fromLngLat(lon, lat)
        }
    }

    private fun finishWithResult(value: String) {
        val resultIntent = Intent()
        resultIntent.putExtra("value", value)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
