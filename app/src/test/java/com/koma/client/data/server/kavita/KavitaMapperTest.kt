package com.koma.client.data.server.kavita

import com.google.common.truth.Truth.assertThat
import com.koma.client.data.server.kavita.dto.KavitaChapterDto
import com.koma.client.data.server.kavita.dto.KavitaLibraryDto
import com.koma.client.data.server.kavita.dto.KavitaSeriesDto
import com.koma.client.data.server.kavita.dto.KavitaSeriesMetadataDto
import com.koma.client.data.server.kavita.dto.KavitaGenreDto
import com.koma.client.data.server.kavita.dto.KavitaTagDto
import com.koma.client.data.server.kavita.dto.KavitaPersonDto
import com.koma.client.domain.model.MediaType
import org.junit.Test

class KavitaMapperTest {

    private val baseUrl = "https://kavita.local"
    private val serverId = "kavita-server-1"

    // ─── Library mapping ──────────────────────────────────────────────────────

    @Test
    fun library_int_id_is_stringified() {
        val dto = KavitaLibraryDto(id = 42, name = "Manga")
        val result = dto.toDomain(serverId)
        assertThat(result.id).isEqualTo("42")
    }

    @Test
    fun library_maps_name_and_serverId() {
        val dto = KavitaLibraryDto(id = 1, name = "Comics")
        val result = dto.toDomain(serverId)
        assertThat(result.name).isEqualTo("Comics")
        assertThat(result.serverId).isEqualTo(serverId)
    }

    // ─── Series mapping ───────────────────────────────────────────────────────

    @Test
    fun series_int_id_is_stringified() {
        val dto = KavitaSeriesDto(id = 7, name = "Berserk", libraryId = 1)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.id).isEqualTo("7")
    }

    @Test
    fun series_libraryId_is_stringified() {
        val dto = KavitaSeriesDto(id = 1, name = "Berserk", libraryId = 3)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.libraryId).isEqualTo("3")
    }

    @Test
    fun series_uses_localizedName_when_present() {
        val dto = KavitaSeriesDto(
            id = 1,
            name = "Berserk",
            localizedName = "베르세르크",
            libraryId = 1,
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.title).isEqualTo("베르세르크")
    }

    @Test
    fun series_falls_back_to_name_when_localizedName_blank() {
        val dto = KavitaSeriesDto(
            id = 1,
            name = "Berserk",
            localizedName = "",
            libraryId = 1,
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.title).isEqualTo("Berserk")
    }

    @Test
    fun series_thumb_url_uses_image_cover_endpoint() {
        val dto = KavitaSeriesDto(id = 5, name = "Test", libraryId = 1)
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/api/Image/series-cover?seriesId=5")
    }

    @Test
    fun series_summary_is_null_when_blank() {
        val dto = KavitaSeriesDto(
            id = 1,
            name = "Test",
            libraryId = 1,
            metadata = KavitaSeriesMetadataDto(summary = ""),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.summary).isNull()
    }

    @Test
    fun series_maps_genres_and_tags() {
        val dto = KavitaSeriesDto(
            id = 1,
            name = "Test",
            libraryId = 1,
            metadata = KavitaSeriesMetadataDto(
                genres = listOf(KavitaGenreDto(1, "Action"), KavitaGenreDto(2, "Fantasy")),
                tags = listOf(KavitaTagDto(1, "Shonen")),
            ),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.genres).containsExactly("Action", "Fantasy")
        assertThat(result.tags).containsExactly("Shonen")
    }

    @Test
    fun series_maps_publicationStatus_ongoing() {
        val dto = KavitaSeriesDto(
            id = 1, name = "Test", libraryId = 1,
            metadata = KavitaSeriesMetadataDto(publicationStatus = 0),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.status).isEqualTo("Ongoing")
    }

    @Test
    fun series_maps_publicationStatus_completed() {
        val dto = KavitaSeriesDto(
            id = 1, name = "Test", libraryId = 1,
            metadata = KavitaSeriesMetadataDto(publicationStatus = 2),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.status).isEqualTo("Completed")
    }

    @Test
    fun series_maps_releaseYear_when_positive() {
        val dto = KavitaSeriesDto(
            id = 1, name = "Test", libraryId = 1,
            metadata = KavitaSeriesMetadataDto(releaseYear = 2003),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.releaseDate).isEqualTo("2003")
    }

    @Test
    fun series_releaseDate_null_when_year_zero() {
        val dto = KavitaSeriesDto(
            id = 1, name = "Test", libraryId = 1,
            metadata = KavitaSeriesMetadataDto(releaseYear = 0),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.releaseDate).isNull()
    }

    // ─── Chapter → Book mapping ───────────────────────────────────────────────

    @Test
    fun chapter_int_id_is_stringified() {
        val dto = KavitaChapterDto(id = 99, number = "1", pages = 20, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.id).isEqualTo("99")
    }

    @Test
    fun chapter_seriesId_is_stringified() {
        val dto = KavitaChapterDto(id = 1, number = "1", pages = 20, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 12)
        assertThat(result.seriesId).isEqualTo("12")
    }

    @Test
    fun chapter_thumb_url_uses_chapter_cover_endpoint() {
        val dto = KavitaChapterDto(id = 33, number = "1", pages = 20, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/api/Image/chapter-cover?chapterId=33")
    }

    @Test
    fun chapter_uses_title_when_available() {
        val dto = KavitaChapterDto(
            id = 1, number = "1", pages = 20,
            title = "The Black Swordsman", seriesId = 5,
        )
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.title).isEqualTo("The Black Swordsman")
    }

    @Test
    fun chapter_title_falls_back_to_chapter_number() {
        val dto = KavitaChapterDto(
            id = 1, number = "5", pages = 20,
            title = "", seriesId = 5,
        )
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.title).isEqualTo("Chapter 5")
    }

    @Test
    fun chapter_maps_page_count() {
        val dto = KavitaChapterDto(id = 1, number = "1", pages = 42, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.pageCount).isEqualTo(42)
    }

    @Test
    fun chapter_mediaType_is_image_when_wordCount_zero() {
        val dto = KavitaChapterDto(id = 1, number = "1", pages = 30, wordCount = 0, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.mediaType).isEqualTo(MediaType.IMAGE)
    }

    @Test
    fun chapter_mediaType_is_epub_when_wordCount_positive() {
        val dto = KavitaChapterDto(id = 1, number = "1", pages = 1, wordCount = 50000, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.mediaType).isEqualTo(MediaType.EPUB)
    }

    @Test
    fun chapter_read_progress_is_null_when_pagesRead_zero() {
        val dto = KavitaChapterDto(id = 1, number = "1", pages = 30, pagesRead = 0, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.readProgress).isNull()
    }

    @Test
    fun chapter_read_progress_populated_when_pagesRead_positive() {
        val dto = KavitaChapterDto(
            id = 1, number = "1", pages = 30, pagesRead = 15, seriesId = 5,
        )
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.readProgress).isNotNull()
        assertThat(result.readProgress!!.page).isEqualTo(15)
        assertThat(result.readProgress!!.completed).isFalse()
    }

    @Test
    fun chapter_read_progress_completed_when_pagesRead_equals_pages() {
        val dto = KavitaChapterDto(
            id = 1, number = "1", pages = 20, pagesRead = 20, seriesId = 5,
        )
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.readProgress!!.completed).isTrue()
    }

    @Test
    fun chapter_number_parsed_to_int() {
        val dto = KavitaChapterDto(id = 1, number = "7", pages = 20, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.number).isEqualTo(7)
    }

    @Test
    fun chapter_number_null_when_non_numeric() {
        val dto = KavitaChapterDto(id = 1, number = "special", pages = 20, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5)
        assertThat(result.number).isNull()
    }

    @Test
    fun chapter_seriesTitle_propagated() {
        val dto = KavitaChapterDto(id = 1, number = "1", pages = 20, seriesId = 5)
        val result = dto.toDomain(serverId, baseUrl, seriesId = 5, seriesTitle = "Berserk")
        assertThat(result.seriesTitle).isEqualTo("Berserk")
    }
}
