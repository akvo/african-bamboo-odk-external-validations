package com.akvo.externalodk.navigation

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
    val latestRecordTimestamp: String
)
