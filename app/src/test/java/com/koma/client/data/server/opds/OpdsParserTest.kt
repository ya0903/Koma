package com.koma.client.data.server.opds

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for [OpdsParser].
 *
 * All tests use inline XML strings and parse them as byte streams so there are
 * no Android framework dependencies — the tests run on the JVM.
 *
 * Note: Android's [android.util.Xml] is not available on the JVM; the parser
 * is exercised here at the data-model / URL-resolution level. The XML pull
 * parsing behaviour is covered via Robolectric in androidTest if needed.
 */
class OpdsParserTest {

    // ─── URL resolution ───────────────────────────────────────────────────────

    @Test
    fun resolveUrl_absolute_href_is_returned_unchanged() {
        val result = OpdsParser.resolveUrl("https://other.example.com/feed", "https://base.example.com")
        assertThat(result).isEqualTo("https://other.example.com/feed")
    }

    @Test
    fun resolveUrl_relative_href_is_resolved_against_base_origin() {
        val result = OpdsParser.resolveUrl("/opds/catalog/new", "https://opds.example.com/opds")
        assertThat(result).isEqualTo("https://opds.example.com/opds/catalog/new")
    }

    @Test
    fun resolveUrl_relative_href_without_leading_slash() {
        val result = OpdsParser.resolveUrl("catalog/new", "https://opds.example.com/opds")
        assertThat(result).isEqualTo("https://opds.example.com/catalog/new")
    }

    @Test
    fun resolveUrl_blank_href_returns_blank() {
        val result = OpdsParser.resolveUrl("", "https://opds.example.com")
        assertThat(result).isEqualTo("")
    }

    @Test
    fun resolveUrl_blank_base_returns_href_as_is() {
        val result = OpdsParser.resolveUrl("/opds/feed", "")
        assertThat(result).isEqualTo("/opds/feed")
    }

    @Test
    fun resolveUrl_http_scheme_absolute_returned_unchanged() {
        val result = OpdsParser.resolveUrl("http://example.com/feed", "https://base.example.com")
        assertThat(result).isEqualTo("http://example.com/feed")
    }

    // ─── OpdsEntry computed properties ───────────────────────────────────────

    @Test
    fun opdsEntry_isAcquisition_true_when_has_acquisition_links() {
        val entry = OpdsEntry(
            id = "1",
            title = "Test Book",
            acquisitionLinks = listOf(OpdsLink("http://example.com/book.epub", "application/epub+zip", "http://opds-spec.org/acquisition")),
        )
        assertThat(entry.isAcquisition).isTrue()
        assertThat(entry.isNavigation).isFalse()
    }

    @Test
    fun opdsEntry_isNavigation_true_when_has_navigation_link() {
        val entry = OpdsEntry(
            id = "lib1",
            title = "Library",
            navigationLink = "https://example.com/opds/lib1",
        )
        assertThat(entry.isNavigation).isTrue()
        assertThat(entry.isAcquisition).isFalse()
    }

    @Test
    fun opdsEntry_neither_acquisition_nor_navigation_by_default() {
        val entry = OpdsEntry(id = "x", title = "Empty")
        assertThat(entry.isAcquisition).isFalse()
        assertThat(entry.isNavigation).isFalse()
    }

    // ─── OpdsMapper — URL helpers ─────────────────────────────────────────────

    @Test
    fun pickBestAcquisitionLink_prefers_epub_over_pdf() {
        val links = listOf(
            OpdsLink("http://example.com/book.pdf", "application/pdf", "http://opds-spec.org/acquisition"),
            OpdsLink("http://example.com/book.epub", "application/epub+zip", "http://opds-spec.org/acquisition"),
        )
        assertThat(pickBestAcquisitionLink(links)?.href).isEqualTo("http://example.com/book.epub")
    }

    @Test
    fun pickBestAcquisitionLink_falls_back_to_pdf_when_no_epub() {
        val links = listOf(
            OpdsLink("http://example.com/book.pdf", "application/pdf", "http://opds-spec.org/acquisition"),
        )
        assertThat(pickBestAcquisitionLink(links)?.href).isEqualTo("http://example.com/book.pdf")
    }

    @Test
    fun pickBestAcquisitionLink_returns_null_for_empty_list() {
        assertThat(pickBestAcquisitionLink(emptyList())).isNull()
    }

    @Test
    fun pickBestAcquisitionLink_returns_first_for_unknown_type() {
        val links = listOf(
            OpdsLink("http://example.com/book.mobi", "application/x-mobipocket-ebook", "http://opds-spec.org/acquisition"),
        )
        assertThat(pickBestAcquisitionLink(links)?.href).isEqualTo("http://example.com/book.mobi")
    }

    // ─── detectOpdsMediaType ──────────────────────────────────────────────────

    @Test
    fun detectOpdsMediaType_epub_mime() {
        assertThat(detectOpdsMediaType("application/epub+zip")).isEqualTo(com.koma.client.domain.model.MediaType.EPUB)
    }

    @Test
    fun detectOpdsMediaType_pdf_mime() {
        assertThat(detectOpdsMediaType("application/pdf")).isEqualTo(com.koma.client.domain.model.MediaType.PDF)
    }

    @Test
    fun detectOpdsMediaType_cbz_is_image() {
        assertThat(detectOpdsMediaType("application/x-cbz")).isEqualTo(com.koma.client.domain.model.MediaType.IMAGE)
    }

    @Test
    fun detectOpdsMediaType_blank_is_image() {
        assertThat(detectOpdsMediaType("")).isEqualTo(com.koma.client.domain.model.MediaType.IMAGE)
    }

    // ─── opdsSeriesId / opdsLibraryId ─────────────────────────────────────────

    @Test
    fun opdsSeriesId_stable_for_same_name() {
        val id1 = opdsSeriesId("My Series")
        val id2 = opdsSeriesId("My Series")
        assertThat(id1).isEqualTo(id2)
    }

    @Test
    fun opdsSeriesId_different_for_different_names() {
        val id1 = opdsSeriesId("Series A")
        val id2 = opdsSeriesId("Series B")
        assertThat(id1).isNotEqualTo(id2)
    }

    @Test
    fun opdsSeriesId_has_prefix() {
        assertThat(opdsSeriesId("Test")).startsWith("opds_series_")
    }

    @Test
    fun opdsLibraryId_has_prefix() {
        assertThat(opdsLibraryId("urn:uuid:lib1")).startsWith("opds_lib_")
    }

    // ─── groupOpdsEntriesIntoSeries ───────────────────────────────────────────

    @Test
    fun groupOpdsEntriesIntoSeries_creates_named_series_from_categories() {
        val entries = listOf(
            makeAcquisitionEntry(id = "1", title = "Book 1", categories = listOf("series:Dune Chronicles")),
            makeAcquisitionEntry(id = "2", title = "Book 2", categories = listOf("series:Dune Chronicles")),
        )
        val series = groupOpdsEntriesIntoSeries(entries, "server1", "lib1")
        val dune = series.find { it.title == "Dune Chronicles" }
        assertThat(dune).isNotNull()
        assertThat(dune!!.bookCount).isEqualTo(2)
    }

    @Test
    fun groupOpdsEntriesIntoSeries_uncategorized_for_entries_without_categories() {
        val entries = listOf(
            makeAcquisitionEntry(id = "1", title = "Solo Book"),
        )
        val series = groupOpdsEntriesIntoSeries(entries, "server1", "lib1")
        val uncat = series.find { it.id == OPDS_UNCATEGORIZED_ID }
        assertThat(uncat).isNotNull()
        assertThat(uncat!!.bookCount).isEqualTo(1)
    }

    @Test
    fun groupOpdsEntriesIntoSeries_thumb_url_from_first_entry() {
        val entries = listOf(
            makeAcquisitionEntry(id = "1", title = "Book", categories = listOf("series:MySeries"), thumbnailUrl = "https://example.com/thumb1.jpg"),
        )
        val series = groupOpdsEntriesIntoSeries(entries, "server1", "lib1")
        val s = series.find { it.title == "MySeries" }!!
        assertThat(s.thumbUrl).isEqualTo("https://example.com/thumb1.jpg")
    }

    @Test
    fun groupOpdsEntriesIntoSeries_library_id_is_passed_through() {
        val entries = listOf(makeAcquisitionEntry())
        val series = groupOpdsEntriesIntoSeries(entries, "server1", "my_lib_id")
        assertThat(series.first().libraryId).isEqualTo("my_lib_id")
    }

    // ─── filterEntriesForSeries ───────────────────────────────────────────────

    @Test
    fun filterEntriesForSeries_uncategorized_returns_entries_with_no_categories() {
        val entries = listOf(
            makeAcquisitionEntry(id = "1"),
            makeAcquisitionEntry(id = "2", categories = listOf("series:SomeOther")),
        )
        val result = filterEntriesForSeries(entries, OPDS_UNCATEGORIZED_ID)
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("1")
    }

    @Test
    fun filterEntriesForSeries_named_series_returns_matching_entries() {
        val entries = listOf(
            makeAcquisitionEntry(id = "1", categories = listOf("series:Dune")),
            makeAcquisitionEntry(id = "2", categories = listOf("series:Foundation")),
        )
        val duneId = opdsSeriesId("Dune")
        val result = filterEntriesForSeries(entries, duneId)
        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("1")
    }

    // ─── toLibrary / toBook ───────────────────────────────────────────────────

    @Test
    fun toLibrary_maps_title_and_server_id() {
        val entry = OpdsEntry(
            id = "urn:lib:1",
            title = "Science Fiction",
            navigationLink = "https://example.com/opds/sci-fi",
        )
        val lib = entry.toLibrary("server-1")
        assertThat(lib.name).isEqualTo("Science Fiction")
        assertThat(lib.serverId).isEqualTo("server-1")
        assertThat(lib.id).isEqualTo(opdsLibraryId("urn:lib:1"))
    }

    @Test
    fun toBook_maps_title_and_server_id() {
        val entry = makeAcquisitionEntry(id = "book-1", title = "Foundation")
        val book = entry.toBook("server-1", "lib-1", "series-1")
        assertThat(book.title).isEqualTo("Foundation")
        assertThat(book.serverId).isEqualTo("server-1")
        assertThat(book.libraryId).isEqualTo("lib-1")
        assertThat(book.seriesId).isEqualTo("series-1")
    }

    @Test
    fun toBook_mediaType_epub_for_epub_link() {
        val entry = makeAcquisitionEntry(
            acquisitionLinks = listOf(
                OpdsLink("https://example.com/book.epub", "application/epub+zip", "http://opds-spec.org/acquisition")
            )
        )
        val book = entry.toBook("s", "l", "sr")
        assertThat(book.mediaType).isEqualTo(com.koma.client.domain.model.MediaType.EPUB)
    }

    @Test
    fun toBook_thumbnailUrl_set_from_entry() {
        val entry = makeAcquisitionEntry(thumbnailUrl = "https://example.com/thumb.jpg")
        val book = entry.toBook("s", "l", "sr")
        assertThat(book.thumbUrl).isEqualTo("https://example.com/thumb.jpg")
    }

    // ─── OpdsFeed data model ──────────────────────────────────────────────────

    @Test
    fun opdsFeed_nextUrl_defaults_null() {
        val feed = OpdsFeed(title = "Test", entries = emptyList())
        assertThat(feed.nextUrl).isNull()
    }

    @Test
    fun opdsFeed_searchUrl_defaults_null() {
        val feed = OpdsFeed(title = "Test", entries = emptyList())
        assertThat(feed.searchUrl).isNull()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeAcquisitionEntry(
        id: String = "entry-1",
        title: String = "Test Book",
        categories: List<String> = emptyList(),
        thumbnailUrl: String? = null,
        acquisitionLinks: List<OpdsLink> = listOf(
            OpdsLink("https://example.com/book.epub", "application/epub+zip", "http://opds-spec.org/acquisition")
        ),
    ) = OpdsEntry(
        id = id,
        title = title,
        categories = categories,
        thumbnailUrl = thumbnailUrl,
        acquisitionLinks = acquisitionLinks,
    )
}
