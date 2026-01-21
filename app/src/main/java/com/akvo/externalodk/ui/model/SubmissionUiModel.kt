package com.akvo.externalodk.ui.model

data class SubmissionUiModel(
    val uuid: String,
    val submittedBy: String,
    val submissionTime: String,
    val isSynced: Boolean = true
)
