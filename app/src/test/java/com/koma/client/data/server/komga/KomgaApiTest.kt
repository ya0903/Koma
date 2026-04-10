package com.koma.client.data.server.komga

import com.google.common.truth.Truth.assertThat
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaPageWrapper
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.data.server.komga.dto.KomgaUserDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class KomgaApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: KomgaApi
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KomgaApi::class.java)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun getMe_parses_user() = runTest {
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(KomgaUserDto(id = "u1", email = "test@example.com", roles = listOf("ADMIN"))))
            .addHeader("Content-Type", "application/json"))

        val user = api.getMe()
        assertThat(user.id).isEqualTo("u1")
        assertThat(user.email).isEqualTo("test@example.com")
    }

    @Test
    fun getLibraries_parses_list() = runTest {
        val libs = listOf(
            KomgaLibraryDto(id = "l1", name = "Manga"),
            KomgaLibraryDto(id = "l2", name = "Comics"),
        )
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(libs))
            .addHeader("Content-Type", "application/json"))

        val result = api.getLibraries()
        assertThat(result).hasSize(2)
        assertThat(result[0].name).isEqualTo("Manga")
    }

    @Test
    fun getSeries_parses_page_wrapper() = runTest {
        val page = KomgaPageWrapper(
            content = listOf(
                KomgaSeriesDto(id = "s1", libraryId = "l1", name = "Naruto", booksCount = 72),
            ),
            totalElements = 1,
            totalPages = 1,
            number = 0,
            size = 20,
            first = true,
            last = true,
        )
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(KomgaPageWrapper.serializer(KomgaSeriesDto.serializer()), page))
            .addHeader("Content-Type", "application/json"))

        val result = api.getSeries(libraryId = "l1")
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].name).isEqualTo("Naruto")
        assertThat(result.totalElements).isEqualTo(1)
    }

    @Test
    fun request_includes_correct_path_and_params() = runTest {
        val emptyPage = KomgaPageWrapper<KomgaSeriesDto>()
        server.enqueue(MockResponse()
            .setBody(json.encodeToString(KomgaPageWrapper.serializer(KomgaSeriesDto.serializer()), emptyPage))
            .addHeader("Content-Type", "application/json"))

        api.getSeries(libraryId = "l1", readStatus = "UNREAD", sort = "metadata.titleSort,asc", page = 2, size = 10)
        val request = server.takeRequest()
        assertThat(request.path).contains("library_id=l1")
        assertThat(request.path).contains("status=UNREAD")
        assertThat(request.path).contains("page=2")
        assertThat(request.path).contains("size=10")
    }
}
