package org.akvo.afribamodkvalidator.validation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.akvo.afribamodkvalidator.data.dao.PlotDao
import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.akvo.afribamodkvalidator.data.session.SessionManager
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class PolygonValidationActivity : AppCompatActivity() {

    @Inject
    lateinit var plotDao: PlotDao

    @Inject
    lateinit var sessionManager: SessionManager

    private val validator = PolygonValidator()
    private val overlapChecker = OverlapChecker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get input from ODK Intent
        val polygonData = intent.getStringExtra(EXTRA_SHAPE)
        val plotName = intent.getStringExtra(EXTRA_PLOT_NAME) ?: ""
        val region = intent.getStringExtra(EXTRA_REGION) ?: ""
        val subRegion = intent.getStringExtra(EXTRA_SUB_REGION) ?: ""
        val instanceName = intent.getStringExtra(EXTRA_INSTANCE_NAME) ?: ""

        // Get formId (assetUid) from session
        val formId = sessionManager.getSession()?.assetUid ?: ""

        if (polygonData.isNullOrBlank()) {
            showErrorAndBlock("No polygon data received from form.", null)
            return
        }

        // Step 1: Single-polygon validation (vertex count, area, self-intersection)
        when (val result = validator.validate(polygonData)) {
            is ValidationResult.Success -> {
                // Step 2: Check for overlaps with existing plots
                checkOverlapsAndProceed(
                    polygonData = polygonData,
                    plotName = plotName,
                    region = region,
                    subRegion = subRegion,
                    formId = formId,
                    instanceName = instanceName
                )
            }
            is ValidationResult.Error -> {
                showErrorAndBlock(result.message, polygonData)
            }
        }
    }

    private fun checkOverlapsAndProceed(
        polygonData: String,
        plotName: String,
        region: String,
        subRegion: String,
        formId: String,
        instanceName: String
    ) {
        // Parse the polygon to JTS format
        val polygon = overlapChecker.parseWkt(polygonData)
            ?: validator.parseToJtsPolygon(polygonData)

        if (polygon == null) {
            showErrorAndBlock("Failed to parse polygon for overlap check.", polygonData)
            return
        }

        // Compute bounding box for SQL query
        val bbox = overlapChecker.computeBoundingBox(polygon)
        val polygonWkt = overlapChecker.toWkt(polygon)
        val plotUuid = UUID.randomUUID().toString()

        lifecycleScope.launch {
            try {
                // Query overlap candidates from database
                val candidates = if (region.isNotEmpty()) {
                    plotDao.findOverlapCandidates(
                        region = region,
                        minLat = bbox.minLat,
                        maxLat = bbox.maxLat,
                        minLon = bbox.minLon,
                        maxLon = bbox.maxLon,
                        excludeUuid = plotUuid
                    )
                } else {
                    emptyList()
                }

                // Check for significant overlaps (>= 5%)
                val overlaps = overlapChecker.checkOverlaps(polygon, candidates)

                if (overlaps.isNotEmpty()) {
                    // Found overlapping plots - show error
                    val firstOverlap = overlaps.first()
                    val errorMessage = "New plot for $plotName overlaps with plot for ${firstOverlap.plotName}"
                    showErrorAndBlock(errorMessage, polygonData)
                } else {
                    // No significant overlap - save draft and return success
                    saveDraftPlot(
                        uuid = plotUuid,
                        plotName = plotName,
                        instanceName = instanceName,
                        polygonWkt = polygonWkt,
                        bbox = bbox,
                        formId = formId,
                        region = region,
                        subRegion = subRegion
                    )
                    returnSuccess(polygonData)
                }
            } catch (e: Exception) {
                // Database error - block submission to prevent unvalidated duplicates
                android.util.Log.e(TAG, "Failed to check overlaps", e)
                showErrorAndBlock(
                    "Unable to verify plot overlaps. Please check your connection and try again.",
                    polygonData
                )
            }
        }
    }

    private suspend fun saveDraftPlot(
        uuid: String,
        plotName: String,
        instanceName: String,
        polygonWkt: String,
        bbox: BoundingBox,
        formId: String,
        region: String,
        subRegion: String
    ) {
        val plot = PlotEntity(
            uuid = uuid,
            plotName = plotName,
            instanceName = instanceName,
            polygonWkt = polygonWkt,
            minLat = bbox.minLat,
            maxLat = bbox.maxLat,
            minLon = bbox.minLon,
            maxLon = bbox.maxLon,
            isDraft = true,
            formId = formId,
            region = region,
            subRegion = subRegion
        )
        plotDao.insertOrUpdate(plot)
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

    companion object {
        private const val TAG = "PolygonValidationActivity"
        const val EXTRA_SHAPE = "shape"
        const val EXTRA_PLOT_NAME = "plot_name"
        const val EXTRA_REGION = "region"
        const val EXTRA_SUB_REGION = "sub_region"
        const val EXTRA_INSTANCE_NAME = "instance_name"
    }
}
