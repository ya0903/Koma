package com.koma.client.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainModelsTest {

    @Test
    fun library_equality_by_value() {
        val a = Library(id = "1", serverId = "s", name = "Manga", unreadCount = 5)
        val b = Library(id = "1", serverId = "s", name = "Manga", unreadCount = 5)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun series_defaults_bookCount_and_unreadCount_to_zero() {
        val s = Series(id = "1", serverId = "s", libraryId = "l", title = "Title")
        assertThat(s.bookCount).isEqualTo(0)
        assertThat(s.unreadCount).isEqualTo(0)
        assertThat(s.thumbUrl).isNull()
    }

    @Test
    fun book_mediaType_image_vs_epub() {
        val image = Book(id = "1", serverId = "s", seriesId = "sr", title = "Vol 1", pageCount = 200, mediaType = MediaType.IMAGE)
        val epub = Book(id = "2", serverId = "s", seriesId = "sr", title = "Novel", pageCount = 0, mediaType = MediaType.EPUB)
        assertThat(image.mediaType).isEqualTo(MediaType.IMAGE)
        assertThat(epub.mediaType).isEqualTo(MediaType.EPUB)
    }

    @Test
    fun readProgress_defaults() {
        val p = ReadProgress(bookId = "b1", page = 0)
        assertThat(p.completed).isFalse()
        assertThat(p.locator).isNull()
    }

    @Test
    fun bookmark_either_page_or_locator() {
        val byPage = Bookmark(id = "1", bookId = "b", page = 5, locator = null, note = null, createdAtEpochMs = 0)
        val byLocator = Bookmark(id = "2", bookId = "b", page = null, locator = "epub-cfi:/6/2", note = "start", createdAtEpochMs = 0)
        assertThat(byPage.page).isEqualTo(5)
        assertThat(byLocator.locator).isNotNull()
    }

    @Test
    fun thumbKind_has_expected_variants() {
        assertThat(ThumbKind.values().toSet()).containsExactly(ThumbKind.LIBRARY, ThumbKind.SERIES, ThumbKind.BOOK)
    }
}
