package com.koma.client.work

import android.content.Context
import com.koma.client.di.BaseOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val name: String? = null,
    val body: String? = null,
)

data class UpdateInfo(
    val available: Boolean,
    val latestVersion: String = "",
    val currentVersion: String = "",
    val releaseUrl: String = "",
)

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    @BaseOkHttpClient private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val githubOwner = "ya0903"
    private val githubRepo = "Koma"

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersion()
        try {
            val url = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val info = UpdateInfo(available = false, currentVersion = currentVersion)
                _updateInfo.value = info
                return@withContext info
            }

            val body = response.body?.string() ?: run {
                val info = UpdateInfo(available = false, currentVersion = currentVersion)
                _updateInfo.value = info
                return@withContext info
            }

            val release = json.decodeFromString<GithubRelease>(body)
            val latestVersion = release.tagName.trimStart('v')
            val isNewer = isVersionNewer(latestVersion, currentVersion)

            val info = UpdateInfo(
                available = isNewer,
                latestVersion = latestVersion,
                currentVersion = currentVersion,
                releaseUrl = release.htmlUrl,
            )
            _updateInfo.value = info
            info
        } catch (e: Exception) {
            val info = UpdateInfo(available = false, currentVersion = currentVersion)
            _updateInfo.value = info
            info
        }
    }

    private fun getCurrentVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
    } catch (e: Exception) {
        "0.1.0"
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
