package com.koma.client.data.server.calibreweb

import com.koma.client.data.server.calibreweb.dto.CalibreBookDto
import com.koma.client.data.server.calibreweb.dto.CalibreListBooksResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service for Calibre-Web's JSON Ajax endpoints.
 *
 * Calibre-Web exposes these endpoints via its own frontend JavaScript. They are not
 * officially documented as a public API but are stable across versions. We prefer them
 * over the OPDS XML feed because JSON is easier to parse.
 *
 * Auth: HTTP Basic is added via an OkHttp interceptor in [CalibreWebMediaServer].
 */
interface CalibreWebApi {

    /**
     * Returns a paginated list of all books in the library.
     * Default sort is by timestamp descending (most-recently-added first).
     */
    @GET("ajax/listbooks")
    suspend fun listBooks(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 500,
        @Query("sort") sort: String = "timestamp",
        @Query("order") order: String = "desc",
    ): CalibreListBooksResponse

    /**
     * Returns the detail for a single book by its integer ID.
     */
    @GET("ajax/book/{id}")
    suspend fun getBook(@Path("id") id: Int): CalibreBookDto

    /**
     * Full-text search across title, authors, series, and tags.
     * Returns the same shape as [listBooks].
     */
    @GET("ajax/search")
    suspend fun search(@Query("query") query: String): CalibreListBooksResponse

    /**
     * Auth-check: attempt to fetch a single book entry.
     * A 401 response means credentials are wrong or Basic auth is disabled.
     */
    @GET("ajax/listbooks")
    suspend fun checkAuth(@Query("limit") limit: Int = 1): CalibreListBooksResponse
}
