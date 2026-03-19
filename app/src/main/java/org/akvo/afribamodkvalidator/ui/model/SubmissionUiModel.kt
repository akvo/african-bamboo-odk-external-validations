package org.akvo.afribamodkvalidator.ui.model

data class SubmissionUiModel(
    val uuid: String,
    val koboId: String,
    val displayTitle: String,
    val syncedOnText: String,
    val submissionTimestamp: Long,
    val isSynced: Boolean = true
)
