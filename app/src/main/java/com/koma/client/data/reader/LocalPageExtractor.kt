package com.koma.client.data.reader

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPageExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Extract page file URIs from a local CBZ file.
     * Returns a list of file:// URIs for each page image, sorted by filename.
     * CBZ files are ZIP archives containing image files.
     */
    suspend fun extractPages(localFilePath: String): List<String> = withContext(Dispatchers.IO) {
        val cbzFile = File(localFilePath)

        val extractDir = File(context.cacheDir, "pages/${cbzFile.nameWithoutExtension}")
        if (extractDir.exists() && extractDir.listFiles()?.isNotEmpty() == true) {
            // Already extracted — return cached pages without needing the original file
            return@withContext getPageUris(extractDir)
        }

        if (!cbzFile.exists()) throw FileNotFoundException("File not found: $localFilePath")

        extractDir.mkdirs()

        ZipFile(cbzFile).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && isImageFile(it.name) }
                .forEach { entry ->
                    val outFile = File(extractDir, entry.name.substringAfterLast('/'))
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
        }

        getPageUris(extractDir)
    }

    private fun getPageUris(dir: File): List<String> {
        return dir.listFiles()
            ?.filter { isImageFile(it.name) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { Uri.fromFile(it).toString() }
            ?: emptyList()
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") ||
            lower.endsWith(".gif") || lower.endsWith(".bmp")
    }

    /**
     * Clean up extracted pages for a book (matched by filename prefix).
     */
    fun cleanupPages(bookId: String) {
        val pagesDir = File(context.cacheDir, "pages")
        pagesDir.listFiles()?.forEach { dir ->
            if (dir.name.startsWith(bookId)) {
                dir.deleteRecursively()
            }
        }
    }
}
