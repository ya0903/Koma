package com.koma.client.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.koma.client.di.BaseOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @BaseOkHttpClient private val okHttpClient: OkHttpClient,
) {
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    suspend fun downloadAndInstall(apkUrl: String) = withContext(Dispatchers.IO) {
        _downloading.value = true
        _progress.value = 0f

        try {
            val request = Request.Builder().url(apkUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

            val body = response.body ?: throw Exception("Empty response")
            val totalBytes = body.contentLength()

            val apkFile = File(context.cacheDir, "koma-update.apk")
            apkFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            _progress.value = bytesRead.toFloat() / totalBytes
                        }
                    }
                }
            }

            _progress.value = 1f
            installApk(apkFile)
        } finally {
            _downloading.value = false
        }
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
