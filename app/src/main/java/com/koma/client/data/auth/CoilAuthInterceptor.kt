package com.koma.client.data.auth

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches Basic auth headers to Coil image requests.
 *
 * Uses an in-memory cache populated via [setActiveServer] to avoid any suspend/blocking
 * calls on the OkHttp dispatcher thread. Call [setActiveServer] whenever the active
 * server changes (e.g. from [ServerRepositoryImpl.setActive]).
 */
@Singleton
class CoilAuthInterceptor @Inject constructor(
    private val credentialStore: CredentialStore,
) : Interceptor {

    // Triple<host, username, password> — written on the main thread, read on OkHttp threads
    @Volatile
    private var cache: Triple<String, String, String>? = null

    /**
     * Populate (or refresh) the auth cache for the currently active server.
     * Must be called whenever the active server is set or changed.
     */
    fun setActiveServer(serverId: String, host: String) {
        val user = credentialStore.getUsername(serverId) ?: ""
        val pass = credentialStore.getPassword(serverId) ?: ""
        cache = Triple(host, user, pass)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cached = cache
        if (cached != null && request.url.host == cached.first) {
            val authedRequest = request.newBuilder()
                .header("Authorization", Credentials.basic(cached.second, cached.third))
                .build()
            return chain.proceed(authedRequest)
        }
        return chain.proceed(request)
    }
}
