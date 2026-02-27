package org.akvo.afribamodkvalidator.data.repository

sealed class SyncProgress {
    data class Downloading(val processed: Int, val total: Int) : SyncProgress()
    data class Complete(val inserted: Int, val rejected: Int, val restored: Int = 0) : SyncProgress()
}
