package com.koma.client.data.server.opds

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HTTP client for OPDS Atom feeds.
 *
 * OPDS is XML-based and not JSON, so we skip Retrofit and use OkHttp directly.
 * The authenticated [OkHttpClient] is built externally (with a Basic-auth interceptor)
 * and passed in.
 *
 * All network calls are dispatched on [Dispatchers.IO].
 */
class OpdsApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
) {
    /**
     * Fetches and parses an OPDS Atom feed from [url].
     *
     * @throws IOException if the network request fails.
     * @throws HttpException if the server returns a non-2xx status.
     * @throws XmlPullParserException if the response body is not valid XML.
     */
    suspend fun getFeed(url: String): OpdsFeed = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/atom+xml, application/xml, text/xml, */*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw OpdsHttpException(response.code, "OPDS request failed: ${response.code} ${response.message}")
            }
            val body = response.body
                ?: throw OpdsHttpException(response.code, "Empty response body from OPDS feed")
            body.byteStream().use { stream ->
                OpdsParser.parse(stream, url)
            }
        }
    }

    /** Fetches the root OPDS catalog feed (typically at /opds or /opds/). */
    suspend fun getRootFeed(): OpdsFeed = getFeed("${baseUrl.trimEnd('/')}/opds")
}

/** Thrown when the OPDS server returns a non-2xx HTTP status. */
class OpdsHttpException(val code: Int, message: String) : Exception(message)
