package org.akvo.afribamodkvalidator.data.repository

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import org.akvo.afribamodkvalidator.BuildConfig
import org.akvo.afribamodkvalidator.data.dto.GitHubReleaseDto
import org.akvo.afribamodkvalidator.data.network.GitHubApiService
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateResult {
    data class Available(
        val release: GitHubReleaseDto,
        val apkUrl: String,
        val apkSize: Long
    ) : UpdateResult()

    data object UpToDate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

@Singleton
class AppUpdateRepository @Inject constructor(
    private val gitHubApiService: GitHubApiService,
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var activeDownloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    suspend fun checkForUpdate(): UpdateResult {
        return try {
            val parts = BuildConfig.GITHUB_REPO.split("/")
            if (parts.size != 2) {
                return UpdateResult.Error("Invalid GITHUB_REPO configuration")
            }
            val (owner, repo) = parts
            val release = gitHubApiService.getLatestRelease(owner, repo)
            val remoteVersion = release.tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (isNewerVersion(remoteVersion, currentVersion)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    UpdateResult.Available(
                        release = release,
                        apkUrl = apkAsset.browserDownloadUrl,
                        apkSize = apkAsset.size
                    )
                } else {
                    UpdateResult.Error("No APK found in release. Visit GitHub to download manually.")
                }
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update", e)
            UpdateResult.Error(e.message ?: "Failed to check for updates")
        }
    }

    fun downloadApk(url: String, fileName: String): Long {
        val uri = Uri.parse(url)
        require(uri.scheme == "https") { "APK download URL must use HTTPS" }

        cleanupOldApks()

        val request = DownloadManager.Request(uri)
            .setTitle("Downloading update")
            .setDescription("Downloading $fileName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        activeDownloadId = downloadManager.enqueue(request)
        return activeDownloadId
    }

    fun registerDownloadCompleteReceiver(onComplete: (Long) -> Unit) {
        unregisterDownloadCompleteReceiver()

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == activeDownloadId) {
                    onComplete(downloadId)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(downloadReceiver, filter)
        }
    }

    fun unregisterDownloadCompleteReceiver() {
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
            downloadReceiver = null
        }
    }

    /**
     * Installs the downloaded APK via the system package installer.
     *
     * @return true if the install intent was launched successfully, false otherwise.
     */
    fun installApk(downloadId: Long): Boolean {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)

        return downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return@use false

            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return@use false
            val status = cursor.getInt(statusIndex)

            if (status != DownloadManager.STATUS_SUCCESSFUL) return@use false

            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (uriIndex < 0) return@use false
            val localUri = cursor.getString(uriIndex) ?: return@use false
            val path = Uri.parse(localUri).path ?: return@use false
            val file = File(path)

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        }
    }

    fun isOnMeteredConnection(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun cleanupOldApks() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            downloadsDir.listFiles()?.filter {
                it.name.startsWith(APK_FILE_PREFIX) && it.name.endsWith(".apk")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup old APKs", e)
        }
    }

    companion object {
        private const val TAG = "AppUpdateRepository"
        const val APK_FILE_PREFIX = "afribamodk-update-"

        fun isNewerVersion(remote: String, current: String): Boolean {
            fun parseVersion(v: String): List<Int> =
                v.removePrefix("v").split(".").map { segment ->
                    segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                }

            val r = parseVersion(remote)
            val c = parseVersion(current)
            for (i in 0 until maxOf(r.size, c.size)) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv > cv) return true
                if (rv < cv) return false
            }
            return false
        }
    }
}
