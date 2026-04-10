package com.koma.client.data.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Represents a parsed EPUB book ready for rendering.
 */
data class EpubBook(
    val title: String,
    val chapters: List<EpubChapter>,
    val extractDir: File,
    /** Base directory of the OPF file (relative to extract root). */
    val contentBasePath: String,
)

data class EpubChapter(
    val title: String,
    val href: String,
)

@Singleton
class EpubParser @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Extracts the EPUB zip and parses its structure.
     * Returns an [EpubBook] with chapter order from the spine.
     */
    suspend fun parse(epubFile: File, bookId: String): EpubBook = withContext(Dispatchers.IO) {
        val extractDir = File(context.cacheDir, "epub_extract/$bookId")
        if (!extractDir.exists()) {
            extractDir.mkdirs()
            extractZip(epubFile, extractDir)
        }

        // 1. Parse META-INF/container.xml to find content.opf path
        val containerFile = File(extractDir, "META-INF/container.xml")
        if (!containerFile.exists()) {
            throw Exception("Invalid EPUB: missing META-INF/container.xml")
        }
        val opfPath = parseContainerXml(containerFile)

        // 2. Parse the OPF file
        val opfFile = File(extractDir, opfPath)
        if (!opfFile.exists()) {
            throw Exception("Invalid EPUB: OPF file not found at $opfPath")
        }
        val contentBasePath = opfPath.substringBeforeLast('/', "")

        parseOpf(opfFile, extractDir, contentBasePath)
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun parseContainerXml(containerFile: File): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(containerFile)
        val rootfiles = doc.getElementsByTagName("rootfile")
        if (rootfiles.length == 0) {
            throw Exception("Invalid EPUB: no rootfile in container.xml")
        }
        return (rootfiles.item(0) as Element).getAttribute("full-path")
    }

    private fun parseOpf(opfFile: File, extractDir: File, contentBasePath: String): EpubBook {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(opfFile)

        // Get title
        val titleElements = doc.getElementsByTagName("dc:title")
        val title = if (titleElements.length > 0) {
            titleElements.item(0).textContent ?: "Untitled"
        } else "Untitled"

        // Build manifest map: id -> href
        val manifestItems = doc.getElementsByTagName("item")
        val manifest = mutableMapOf<String, Pair<String, String>>() // id -> (href, mediaType)
        for (i in 0 until manifestItems.length) {
            val el = manifestItems.item(i) as Element
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            val mediaType = el.getAttribute("media-type")
            manifest[id] = Pair(href, mediaType)
        }

        // Parse spine order
        val spineItems = doc.getElementsByTagName("itemref")
        val chapters = mutableListOf<EpubChapter>()
        for (i in 0 until spineItems.length) {
            val el = spineItems.item(i) as Element
            val idref = el.getAttribute("idref")
            val item = manifest[idref] ?: continue
            val (href, mediaType) = item

            // Only include XHTML content documents
            if (mediaType.contains("xhtml") || mediaType.contains("html") || href.endsWith(".xhtml") || href.endsWith(".html")) {
                val fullHref = if (contentBasePath.isNotEmpty()) "$contentBasePath/$href" else href
                chapters.add(
                    EpubChapter(
                        title = "Chapter ${chapters.size + 1}",
                        href = fullHref,
                    )
                )
            }
        }

        return EpubBook(
            title = title,
            chapters = chapters,
            extractDir = extractDir,
            contentBasePath = contentBasePath,
        )
    }

}
