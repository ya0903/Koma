package com.koma.client.data.server.calibreweb

import com.google.common.truth.Truth.assertThat
import com.koma.client.data.server.calibreweb.dto.CalibreBookDto
import com.koma.client.domain.model.MediaType
import org.junit.Test

class CalibreWebMapperTest {

    private val baseUrl = "https://calibre.local"
    private val serverId = "calibre-server-1"

    // ─── Book ID stringification ──────────────────────────────────────────────

    @Test
    fun book_int_id_is_stringified() {
        val dto = makeBook(id = 42)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.id).isEqualTo("42")
    }

    @Test
    fun book_serverId_is_set() {
        val dto = makeBook(id = 1)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.serverId).isEqualTo(serverId)
    }

    // ─── Title mapping ────────────────────────────────────────────────────────

    @Test
    fun book_title_is_preserved() {
        val dto = makeBook(title = "Foundation")
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.title).isEqualTo("Foundation")
    }

    // ─── Series grouping ──────────────────────────────────────────────────────

    @Test
    fun book_with_series_gets_series_id() {
        val dto = makeBook(series = "Dune Chronicles")
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.seriesId).isEqualTo(calibreSeriesId("Dune Chronicles"))
    }

    @Test
    fun book_without_series_gets_uncategorized_id() {
        val dto = makeBook(series = null)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.seriesId).isEqualTo(CALIBRE_UNCATEGORIZED_ID)
    }

    @Test
    fun book_with_blank_series_gets_uncategorized_id() {
        val dto = makeBook(series = "")
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.seriesId).isEqualTo(CALIBRE_UNCATEGORIZED_ID)
    }

    @Test
    fun series_index_is_mapped_to_number_as_int() {
        val dto = makeBook(seriesIndex = 3.0f)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.number).isEqualTo(3)
    }

    @Test
    fun series_index_null_means_number_null() {
        val dto = makeBook(seriesIndex = null)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.number).isNull()
    }

    // ─── Cover / thumbnail URL ────────────────────────────────────────────────

    @Test
    fun thumb_url_uses_cover_endpoint() {
        val dto = makeBook(id = 7)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/cover/7")
    }

    @Test
    fun thumb_url_trims_trailing_slash_from_baseUrl() {
        val dto = makeBook(id = 5)
        val result = dto.toDomain(serverId, "$baseUrl/")
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/cover/5")
    }

    // ─── MediaType detection ──────────────────────────────────────────────────

    @Test
    fun mediaType_is_epub_when_epub_in_formats() {
        assertThat(detectMediaType(listOf("EPUB"))).isEqualTo(MediaType.EPUB)
    }

    @Test
    fun mediaType_is_epub_when_epub_and_pdf_both_present() {
        assertThat(detectMediaType(listOf("EPUB", "PDF"))).isEqualTo(MediaType.EPUB)
    }

    @Test
    fun mediaType_is_pdf_when_only_pdf() {
        assertThat(detectMediaType(listOf("PDF"))).isEqualTo(MediaType.PDF)
    }

    @Test
    fun mediaType_is_image_for_cbz() {
        assertThat(detectMediaType(listOf("CBZ"))).isEqualTo(MediaType.IMAGE)
    }

    @Test
    fun mediaType_is_image_for_empty_formats() {
        assertThat(detectMediaType(emptyList())).isEqualTo(MediaType.IMAGE)
    }

    // ─── Format picking ───────────────────────────────────────────────────────

    @Test
    fun best_format_prefers_epub_over_pdf() {
        assertThat(pickBestFormat(listOf("PDF", "EPUB"))).isEqualTo("EPUB")
    }

    @Test
    fun best_format_prefers_pdf_when_no_epub() {
        assertThat(pickBestFormat(listOf("PDF", "CBZ"))).isEqualTo("PDF")
    }

    @Test
    fun best_format_falls_back_to_first_unknown() {
        assertThat(pickBestFormat(listOf("MOBI"))).isEqualTo("MOBI")
    }

    @Test
    fun best_format_null_when_empty() {
        assertThat(pickBestFormat(emptyList())).isNull()
    }

    // ─── Format parsing ───────────────────────────────────────────────────────

    @Test
    fun parseFormats_splits_and_uppercases() {
        assertThat(parseFormats("epub,pdf")).containsExactly("EPUB", "PDF")
    }

    @Test
    fun parseFormats_handles_spaces() {
        assertThat(parseFormats("EPUB, PDF")).containsExactly("EPUB", "PDF")
    }

    @Test
    fun parseFormats_empty_string_returns_empty_list() {
        assertThat(parseFormats("")).isEmpty()
    }

    // ─── Author parsing ───────────────────────────────────────────────────────

    @Test
    fun parseAuthors_splits_on_pipe() {
        assertThat(parseAuthors("Frank Herbert|Isaac Asimov"))
            .containsExactly("Frank Herbert", "Isaac Asimov")
    }

    @Test
    fun parseAuthors_falls_back_to_comma_split() {
        assertThat(parseAuthors("Frank Herbert, Isaac Asimov"))
            .containsExactly("Frank Herbert", "Isaac Asimov")
    }

    // ─── Download URL ─────────────────────────────────────────────────────────

    @Test
    fun calibreDownloadUrl_builds_correct_path() {
        val url = calibreDownloadUrl(baseUrl, bookId = 5, format = "EPUB")
        assertThat(url).isEqualTo("$baseUrl/opds/download/5/epub/")
    }

    @Test
    fun calibreDownloadUrl_trims_trailing_slash_from_base() {
        val url = calibreDownloadUrl("$baseUrl/", bookId = 3, format = "PDF")
        assertThat(url).isEqualTo("$baseUrl/opds/download/3/pdf/")
    }

    // ─── Series grouping from book list ──────────────────────────────────────

    @Test
    fun groupBooksIntoSeries_creates_named_series_entry() {
        val books = listOf(makeBook(id = 1, series = "Dune"), makeBook(id = 2, series = "Dune"))
        val series = groupBooksIntoSeries(books, serverId, baseUrl)
        val dune = series.find { it.title == "Dune" }
        assertThat(dune).isNotNull()
        assertThat(dune!!.bookCount).isEqualTo(2)
    }

    @Test
    fun groupBooksIntoSeries_creates_uncategorized_for_books_without_series() {
        val books = listOf(makeBook(id = 1, series = null), makeBook(id = 2, series = null))
        val series = groupBooksIntoSeries(books, serverId, baseUrl)
        val uncategorized = series.find { it.id == CALIBRE_UNCATEGORIZED_ID }
        assertThat(uncategorized).isNotNull()
        assertThat(uncategorized!!.bookCount).isEqualTo(2)
    }

    @Test
    fun groupBooksIntoSeries_no_uncategorized_when_all_have_series() {
        val books = listOf(makeBook(id = 1, series = "Series A"))
        val series = groupBooksIntoSeries(books, serverId, baseUrl)
        assertThat(series.none { it.id == CALIBRE_UNCATEGORIZED_ID }).isTrue()
    }

    @Test
    fun groupBooksIntoSeries_series_id_is_stable_for_same_name() {
        val books = listOf(makeBook(id = 1, series = "My Series"), makeBook(id = 2, series = "My Series"))
        val series = groupBooksIntoSeries(books, serverId, baseUrl)
        val allIds = series.filter { it.title == "My Series" }.map { it.id }.toSet()
        assertThat(allIds).hasSize(1)
    }

    @Test
    fun groupBooksIntoSeries_thumb_url_from_first_book() {
        val books = listOf(
            makeBook(id = 10, series = "Series A", seriesIndex = 1.0f),
            makeBook(id = 20, series = "Series A", seriesIndex = 2.0f),
        )
        val series = groupBooksIntoSeries(books, serverId, baseUrl)
        val seriesA = series.find { it.title == "Series A" }!!
        assertThat(seriesA.thumbUrl).isEqualTo("$baseUrl/cover/10")
    }

    @Test
    fun groupBooksIntoSeries_library_id_is_calibre_library_id() {
        val books = listOf(makeBook(id = 1, series = "Test"))
        val series = groupBooksIntoSeries(books, serverId, baseUrl)
        assertThat(series.first().libraryId).isEqualTo(CALIBRE_LIBRARY_ID)
    }

    // ─── filterBooksForSeries ─────────────────────────────────────────────────

    @Test
    fun filterBooksForSeries_uncategorized_returns_books_without_series() {
        val books = listOf(makeBook(id = 1, series = null), makeBook(id = 2, series = "X"))
        val result = filterBooksForSeries(books, CALIBRE_UNCATEGORIZED_ID)
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1)
    }

    @Test
    fun filterBooksForSeries_named_series_returns_matching_books() {
        val books = listOf(
            makeBook(id = 1, series = "Dune"),
            makeBook(id = 2, series = "Foundation"),
        )
        val duneId = calibreSeriesId("Dune")
        val result = filterBooksForSeries(books, duneId)
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1)
    }

    // ─── calibreLibrary ───────────────────────────────────────────────────────

    @Test
    fun calibreLibrary_has_expected_id_and_name() {
        val lib = calibreLibrary(serverId)
        assertThat(lib.id).isEqualTo(CALIBRE_LIBRARY_ID)
        assertThat(lib.name).isEqualTo(CALIBRE_LIBRARY_NAME)
        assertThat(lib.serverId).isEqualTo(serverId)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeBook(
        id: Int = 1,
        title: String = "Test Book",
        series: String? = null,
        seriesIndex: Float? = null,
        formats: String = "EPUB",
        authors: String = "Author One",
    ) = CalibreBookDto(
        id = id,
        title = title,
        authors = authors,
        series = series,
        seriesIndex = seriesIndex,
        formats = formats,
    )
}
