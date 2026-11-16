package com.example.focus.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.focus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val htmlUrl: String,
    val isNewerVersion: Boolean
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val currentVersion: String, val releaseInfo: GitHubReleaseInfo? = null) : UpdateStatus
    data class UpdateAvailable(val releaseInfo: GitHubReleaseInfo) : UpdateStatus
    data class Downloading(val releaseInfo: GitHubReleaseInfo, val progressPercent: Int) : UpdateStatus
    data class ReadyToInstall(val apkFile: File, val releaseInfo: GitHubReleaseInfo) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

object GitHubUpdateManager {
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private var currentDownloadId: Long = -1L

    suspend fun checkForUpdates(owner: String, repo: String) {
        val cleanOwner = owner.trim()
        val cleanRepo = repo.trim()
        if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) {
            _status.value = UpdateStatus.Error("GitHub foydalanuvchi nomi va repozitoriy nomini kiriting.")
            return
        }

        _status.value = UpdateStatus.Checking

        withContext(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/latest"
                val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Focus-Android-App/${BuildConfig.VERSION_NAME}")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    _status.value = UpdateStatus.Error(
                        "Repozitoriy ($cleanOwner/$cleanRepo) yoki Release topilmadi (404). GitHub Releases bo'limiga APK yuklanganligini tekshiring."
                    )
                    return@withContext
                }
                if (responseCode !in 200..299) {
                    _status.value = UpdateStatus.Error("GitHub server xatosi: HTTP $responseCode")
                    return@withContext
                }

                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "v1.0")
                val normalizedRemote = tagName.removePrefix("v").removePrefix("V").trim()
                val title = json.optString("name", tagName).ifBlank { tagName }
                val body = json.optString("body", "Yangilanish tavsifi kiritilmagan.")
                val publishedAt = json.optString("published_at", "").take(10)
                val htmlUrl = json.optString("html_url", "https://github.com/$cleanOwner/$cleanRepo/releases")

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            apkUrl = downloadUrl
                            break
                        }
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()
                val isNewer = isRemoteVersionNewer(currentVersion, normalizedRemote)

                val info = GitHubReleaseInfo(
                    tagName = tagName,
                    versionName = normalizedRemote,
                    title = title,
                    releaseNotes = body,
                    publishedAt = publishedAt,
                    apkDownloadUrl = apkUrl,
                    htmlUrl = htmlUrl,
                    isNewerVersion = isNewer
                )

                if (isNewer) {
                    _status.value = UpdateStatus.UpdateAvailable(info)
                } else {
                    _status.value = UpdateStatus.UpToDate(currentVersion, info)
                }
            } catch (e: Exception) {
                _status.value = UpdateStatus.Error(
                    "Internet yoki ulanish xatosi: ${e.localizedMessage ?: "Tarmoqni tekshiring"}"
                )
            }
        }
    }

    fun downloadAndInstallApk(context: Context, releaseInfo: GitHubReleaseInfo) {
        val apkUrl = releaseInfo.apkDownloadUrl
        if (apkUrl.isNullOrBlank()) {
            // Open GitHub release page in browser if no direct APK asset was uploaded
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                _status.value = UpdateStatus.Error("Release sahifasini ochib bo'lmadi.")
            }
            return
        }

        try {
            val appContext = context.applicationContext
            val fileName = "Focus-${releaseInfo.versionName}.apk"
            val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val destinationFile = File(downloadsDir, fileName)
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Focus ${releaseInfo.tagName} yangilanishi")
                .setDescription("Yangi versiya yuklab olinmoqda...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)

            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            currentDownloadId = dm.enqueue(request)
            _status.value = UpdateStatus.Downloading(releaseInfo, 10)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == currentDownloadId) {
                        try {
                            appContext.unregisterReceiver(this)
                        } catch (_: Exception) {
                        }
                        _status.value = UpdateStatus.ReadyToInstall(destinationFile, releaseInfo)
                        installApk(appContext, destinationFile)
                    }
                }
            }

            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            _status.value = UpdateStatus.Error("Yuklab olishda xatolik: ${e.localizedMessage}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _status.value = UpdateStatus.Error("APK fayli topilmadi.")
                return
            }
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _status.value = UpdateStatus.Error("O'rnatish oynasini ochishda xatolik: ${e.localizedMessage}")
        }
    }

    fun resetStatus() {
        _status.value = UpdateStatus.Idle
    }

    private fun isRemoteVersionNewer(current: String, remote: String): Boolean {
        if (current == remote) return false
        val currentParts = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return remote != current
    }
}
