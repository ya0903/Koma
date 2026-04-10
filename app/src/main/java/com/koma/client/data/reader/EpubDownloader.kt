package com.koma.client.data.reader

import android.content.Context
import com.koma.client.data.auth.CredentialStore
import com.koma.client.data.db.dao.ServerDao
import com.koma.client.di.BaseOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpubDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    @BaseOkHttpClient private val baseOkHttpClient: OkHttpClient,
    private val credentialStore: CredentialStore,
    private val serverDao: ServerDao,
) {
    /**
     * Downloads an EPUB file from the given [fileUrl] to app-private cache.
     * Returns the cached file, or the existing file if already downloaded.
     */
    suspend fun download(bookId: String, fileUrl: String): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "epub")
        cacheDir.mkdirs()
        val file = File(cacheDir, "$bookId.epub")

        // Return cached file if it exists and has content
        if (file.exists() && file.length() > 0) {
            return@withContext file
        }

        val server = serverDao.getActive() ?: throw Exception("No active server")
        val username = credentialStore.getUsername(server.id) ?: ""
        val password = credentialStore.getPassword(server.id) ?: ""

        val client = baseOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", Credentials.basic(username, password))
                        .build()
                )
            }.build()

        val request = Request.Builder().url(fileUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }

            file.outputStream().use { out ->
                response.body?.byteStream()?.copyTo(out)
                    ?: throw Exception("Empty response body")
            }
        }

        file
    }
}
