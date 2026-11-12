package com.example.attention

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {
    private val repoUrl = "https://api.github.com/repos/joxacybers/focus/releases/latest"
    
    private val currentVersion: String by lazy {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(repoUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonObject = JSONObject(response)
                val tagName = jsonObject.getString("tag_name")
                
                val latestVersion = tagName.replace("v", "")
                
                // Direct comparison for versions to avoid repeatedly downloading the same version
                if (latestVersion != currentVersion) {
                    val assets = jsonObject.getJSONArray("assets")
                    if (assets.length() > 0) {
                        val downloadUrl = assets.getJSONObject(0).getString("browser_download_url")
                        withContext(Dispatchers.Main) {
                            downloadAndInstallUpdate(downloadUrl, latestVersion)
                        }
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error checking for updates", e)
        }
        return@withContext false
    }

    private fun downloadAndInstallUpdate(downloadUrl: String, version: String) {
        Toast.makeText(context, "Yangi versiya yuklanmoqda ($version)...", Toast.LENGTH_SHORT).show()

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Focus $version")
            .setDescription("Yangi versiya yuklanmoqda")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "focus_$version.apk")
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(downloadId, downloadManager)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
    }

    private fun installApk(downloadId: Long, downloadManager: DownloadManager) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        }
    }
}