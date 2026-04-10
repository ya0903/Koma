package com.koma.client.data.server.komga

import com.google.common.truth.Truth.assertThat
import com.koma.client.data.server.komga.dto.KomgaBookDto
import com.koma.client.data.server.komga.dto.KomgaBookMetadataDto
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaMediaDto
import com.koma.client.data.server.komga.dto.KomgaReadProgressDto
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.domain.model.MediaType
import org.junit.Test

class KomgaMapperTest {

    private val baseUrl = "https://komga.local"
    private val serverId = "server1"

    @Test
    fun library_maps_id_and_name() {
        val dto = KomgaLibraryDto(id = "lib1", name = "Manga")
        val result = dto.toDomain(serverId)
        assertThat(result.id).isEqualTo("lib1")
        assertThat(result.serverId).isEqualTo(serverId)
        assertThat(result.name).isEqualTo("Manga")
    }

    @Test
    fun series_maps_counts_and_thumb() {
        val dto = KomgaSeriesDto(
            id = "s1",
            libraryId = "lib1",
            name = "One Piece",
            booksCount = 100,
            booksUnreadCount = 5,
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.id).isEqualTo("s1")
        assertThat(result.title).isEqualTo("One Piece")
        assertThat(result.bookCount).isEqualTo(100)
        assertThat(result.unreadCount).isEqualTo(5)
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/api/v1/series/s1/thumbnail")
    }

    @Test
    fun book_maps_mediaType_image_for_zip() {
        val dto = KomgaBookDto(
            id = "b1",
            seriesId = "s1",
            name = "Chapter 1",
            number = 1,
            sizeBytes = 5_000_000,
            media = KomgaMediaDto(mediaType = "application/zip", pagesCount = 20),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(result.pageCount).isEqualTo(20)
        assertThat(result.thumbUrl).isEqualTo("$baseUrl/api/v1/books/b1/thumbnail")
    }

    @Test
    fun book_maps_mediaType_epub() {
        val dto = KomgaBookDto(
            id = "b2",
            seriesId = "s1",
            name = "Novel",
            media = KomgaMediaDto(mediaType = "application/epub+zip", pagesCount = 0),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.mediaType).isEqualTo(MediaType.EPUB)
    }

    @Test
    fun book_maps_mediaType_pdf() {
        val dto = KomgaBookDto(
            id = "b3",
            seriesId = "s1",
            name = "PDF book",
            media = KomgaMediaDto(mediaType = "application/pdf", pagesCount = 50),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.mediaType).isEqualTo(MediaType.PDF)
    }

    @Test
    fun book_uses_metadata_title_when_available() {
        val dto = KomgaBookDto(
            id = "b1",
            seriesId = "s1",
            name = "file_001.cbz",
            metadata = KomgaBookMetadataDto(title = "Chapter 1: The Beginning"),
            media = KomgaMediaDto(pagesCount = 20),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.title).isEqualTo("Chapter 1: The Beginning")
    }

    @Test
    fun book_falls_back_to_name_when_metadata_title_empty() {
        val dto = KomgaBookDto(
            id = "b1",
            seriesId = "s1",
            name = "file_001.cbz",
            metadata = KomgaBookMetadataDto(title = ""),
            media = KomgaMediaDto(pagesCount = 20),
        )
        val result = dto.toDomain(serverId, baseUrl)
        assertThat(result.title).isEqualTo("file_001.cbz")
    }

    @Test
    fun readProgress_maps_correctly() {
        val dto = KomgaReadProgressDto(page = 15, completed = false)
        val result = dto.toDomain("b1")
        assertThat(result.bookId).isEqualTo("b1")
        assertThat(result.page).isEqualTo(15)
        assertThat(result.completed).isFalse()
    }
}
