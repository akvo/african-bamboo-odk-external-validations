package org.akvo.afribamodkvalidator.data.repository

import kotlinx.serialization.Serializable

/**
 * Configuration for extracting plot data from submission rawData.
 *
 * Loaded from assets/plot_extraction_config.json to allow customization
 * without code changes.
 */
@Serializable
data class PlotExtractionConfig(
    /**
     * List of field paths to try for polygon data (in order of priority).
     * First non-empty match is used.
     */
    val polygonFields: List<String>,

    /**
     * List of field names to combine for the plot name (e.g., farmer name parts).
     * Values are joined with spaces.
     */
    val plotNameFields: List<String>,

    /**
     * Field name for the region (e.g., woreda).
     */
    val regionField: String,

    /**
     * Field name for the sub-region (e.g., kebele).
     */
    val subRegionField: String
)
