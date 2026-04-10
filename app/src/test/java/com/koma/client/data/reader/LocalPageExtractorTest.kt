package com.koma.client.data.reader

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LocalPageExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * Creates a CBZ (ZIP) file with the given entry names in the temp folder.
     * Each entry gets 1 byte of placeholder content.
     */
    private fun createCbz(vararg entryNames: String, fileName: String = "test_book.cbz"): File {
        val cbz = tempFolder.newFile(fileName)
        ZipOutputStream(cbz.outputStream()).use { zip ->
            for (name in entryNames) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(0x00))
                zip.closeEntry()
            }
        }
        return cbz
    }

    private fun makeExtractor(): LocalPageExtractor {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        return LocalPageExtractor(ctx)
    }

    @Test
    fun extractPages_returns_correct_count() {
        val cbz = createCbz("001.jpg", "002.jpg", "003.png")
        val extractor = makeExtractor()

        val pages = runBlocking { extractor.extractPages(cbz.absolutePath) }
        assertThat(pages).hasSize(3)
    }

    @Test
    fun extractPages_sorts_by_filename() {
        // Put entries in reverse order to verify sort is applied
        val cbz = createCbz("page003.jpg", "page001.jpg", "page002.jpg", fileName = "sort_test.cbz")
        val extractor = makeExtractor()

        val pages = runBlocking { extractor.extractPages(cbz.absolutePath) }
        assertThat(pages).hasSize(3)
        assertThat(pages[0]).contains("page001")
        assertThat(pages[1]).contains("page002")
        assertThat(pages[2]).contains("page003")
    }

    @Test
    fun extractPages_returns_file_uris() {
        val cbz = createCbz("img.jpg", fileName = "uri_test.cbz")
        val extractor = makeExtractor()

        val pages = runBlocking { extractor.extractPages(cbz.absolutePath) }
        assertThat(pages).hasSize(1)
        assertThat(pages[0]).startsWith("file://")
    }

    @Test
    fun extractPages_filters_non_image_entries() {
        val cbz = createCbz("001.jpg", "notes.txt", "002.png", "Thumbs.db", fileName = "filter_test.cbz")
        val extractor = makeExtractor()

        val pages = runBlocking { extractor.extractPages(cbz.absolutePath) }
        // Only image files should be returned
        assertThat(pages).hasSize(2)
    }

    @Test
    fun extractPages_uses_cache_on_second_call() {
        val cbz = createCbz("001.jpg", "002.jpg", fileName = "cache_test.cbz")
        val extractor = makeExtractor()

        val pages1 = runBlocking { extractor.extractPages(cbz.absolutePath) }
        // Delete the original CBZ to prove second call reads from cache
        cbz.delete()
        val pages2 = runBlocking { extractor.extractPages(cbz.absolutePath) }

        assertThat(pages1).isEqualTo(pages2)
    }

    @Test(expected = FileNotFoundException::class)
    fun extractPages_throws_when_file_missing() {
        val extractor = makeExtractor()
        runBlocking {
            extractor.extractPages("/nonexistent/path/book.cbz")
        }
    }

    @Test
    fun cleanupPages_removes_extracted_dir() {
        val cbz = createCbz("001.jpg", fileName = "cleanup_book.cbz")
        val extractor = makeExtractor()

        runBlocking { extractor.extractPages(cbz.absolutePath) }

        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val pagesDir = File(ctx.cacheDir, "pages")
        assertThat(pagesDir.exists()).isTrue()

        extractor.cleanupPages("cleanup_book")

        val remaining = pagesDir.listFiles()?.filter { it.name.startsWith("cleanup_book") } ?: emptyList()
        assertThat(remaining).isEmpty()
    }
}
