package com.koma.client.data.server.komga

import com.koma.client.data.server.komga.dto.KomgaBookDto
import com.koma.client.data.server.komga.dto.KomgaLibraryDto
import com.koma.client.data.server.komga.dto.KomgaPageDto
import com.koma.client.data.server.komga.dto.KomgaPageWrapper
import com.koma.client.data.server.komga.dto.KomgaReadProgressUpdateDto
import com.koma.client.data.server.komga.dto.KomgaSeriesDto
import com.koma.client.data.server.komga.dto.KomgaUserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface KomgaApi {

    @GET("api/v1/users/me")
    suspend fun getMe(): KomgaUserDto

    @GET("api/v1/libraries")
    suspend fun getLibraries(): List<KomgaLibraryDto>

    @GET("api/v1/series")
    suspend fun getSeries(
        @Query("library_id") libraryId: String? = null,
        @Query("status") readStatus: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): KomgaPageWrapper<KomgaSeriesDto>

    @GET("api/v1/series/{seriesId}")
    suspend fun getSeriesDetail(@Path("seriesId") seriesId: String): KomgaSeriesDto

    @GET("api/v1/series/{seriesId}/books")
    suspend fun getSeriesBooks(
        @Path("seriesId") seriesId: String,
        @Query("sort") sort: String? = "metadata.numberSort,asc",
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 500,
    ): KomgaPageWrapper<KomgaBookDto>

    @GET("api/v1/books/{bookId}")
    suspend fun getBook(@Path("bookId") bookId: String): KomgaBookDto

    @GET("api/v1/books/{bookId}/pages")
    suspend fun getBookPages(@Path("bookId") bookId: String): List<KomgaPageDto>

    @PATCH("api/v1/books/{bookId}/read-progress")
    suspend fun updateReadProgress(
        @Path("bookId") bookId: String,
        @Body body: KomgaReadProgressUpdateDto,
    )

    @GET("api/v1/series/new")
    suspend fun getNewSeries(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): KomgaPageWrapper<KomgaSeriesDto>

    @GET("api/v1/books/ondeck")
    suspend fun getOnDeck(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): KomgaPageWrapper<KomgaBookDto>

    @GET("api/v1/books")
    suspend fun getBooks(
        @Query("sort") sort: String? = null,
        @Query("read_status") readStatus: String? = null,
        @Query("library_id") libraryId: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): KomgaPageWrapper<KomgaBookDto>

    @GET("api/v1/genres")
    suspend fun getGenres(): List<String>

    @GET("api/v1/tags/series")
    suspend fun getSeriesTags(): List<String>

    @GET("api/v1/publishers")
    suspend fun getPublishers(): List<String>
}
