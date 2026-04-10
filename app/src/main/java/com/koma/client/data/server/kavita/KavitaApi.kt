package com.koma.client.data.server.kavita

import com.koma.client.data.server.kavita.dto.KavitaChapterDto
import com.koma.client.data.server.kavita.dto.KavitaLibraryDto
import com.koma.client.data.server.kavita.dto.KavitaLoginRequest
import com.koma.client.data.server.kavita.dto.KavitaSeriesFilterDto
import com.koma.client.data.server.kavita.dto.KavitaLoginResponse
import com.koma.client.data.server.kavita.dto.KavitaProgressDto
import com.koma.client.data.server.kavita.dto.KavitaSearchResultDto
import com.koma.client.data.server.kavita.dto.KavitaSeriesDto
import com.koma.client.data.server.kavita.dto.KavitaVolumeDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface KavitaApi {

    @POST("api/Account/login")
    suspend fun login(@Body body: KavitaLoginRequest): KavitaLoginResponse

    @POST("api/Account/refresh-token")
    suspend fun refreshToken(): KavitaLoginResponse

    @GET("api/Library/libraries")
    suspend fun getLibraries(): List<KavitaLibraryDto>

    @POST("api/Series/all")
    suspend fun getSeries(
        @Body body: KavitaSeriesFilterDto = KavitaSeriesFilterDto(),
    ): List<KavitaSeriesDto>

    @GET("api/Series/{id}")
    suspend fun getSeriesDetail(@Path("id") id: Int): KavitaSeriesDto

    @GET("api/Series/volumes")
    suspend fun getVolumes(@Query("seriesId") seriesId: Int): List<KavitaVolumeDto>

    @GET("api/Series/chapter")
    suspend fun getChapter(@Query("chapterId") chapterId: Int): KavitaChapterDto

    @Streaming
    @GET("api/Reader/file")
    suspend fun getChapterFile(@Query("chapterId") chapterId: Int): ResponseBody

    @POST("api/Reader/progress")
    suspend fun updateProgress(@Body body: KavitaProgressDto)

    @GET("api/Search/search")
    suspend fun search(@Query("queryString") query: String): KavitaSearchResultDto
}
