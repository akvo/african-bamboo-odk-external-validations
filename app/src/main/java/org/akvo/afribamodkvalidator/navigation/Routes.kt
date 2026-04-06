package org.akvo.afribamodkvalidator.navigation

import kotlinx.serialization.Serializable

@Serializable
object Login

@Serializable
data class Loading(val type: LoadingType)

@Serializable
enum class LoadingType {
    DOWNLOAD,
    RESYNC
}

@Serializable
data class DownloadComplete(
    val totalEntries: Int,
    val latestSubmissionDate: String
)

@Serializable
object Home

@Serializable
data class SubmissionDetail(val uuid: String)

@Serializable
data class SyncComplete(
    val addedRecords: Int,
    val updatedRecords: Int,
    val rejectedRecords: Int,
    val latestRecordTimestamp: String
)

@Serializable
object OfflineMap

@Serializable
data class GeoMapView(val uuid: String, val fieldKey: String)

@Serializable
object Settings

@Serializable
object Support
