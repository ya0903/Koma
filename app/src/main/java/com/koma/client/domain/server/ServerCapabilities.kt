package com.koma.client.domain.server

data class ServerCapabilities(
    val serverProgressSync: Boolean,
    val serverBookmarks: Boolean,
    val nativeSearch: Boolean,
    val collections: Boolean,
    val readlists: Boolean,
    val nativeSeries: Boolean,
) {
    companion object {
        fun komgaDefaults() = ServerCapabilities(
            serverProgressSync = true,
            serverBookmarks = false,
            nativeSearch = true,
            collections = true,
            readlists = true,
            nativeSeries = true,
        )

        fun kavitaDefaults() = ServerCapabilities(
            serverProgressSync = true,
            serverBookmarks = false,
            nativeSearch = true,
            collections = true,
            readlists = true,
            nativeSeries = true,
        )

        fun calibreWebDefaults() = ServerCapabilities(
            serverProgressSync = false,
            serverBookmarks = false,
            nativeSearch = true,
            collections = false,
            readlists = false,
            nativeSeries = false,
        )

        fun opdsDefaults() = ServerCapabilities(
            serverProgressSync = false,  // OPDS has no progress API
            serverBookmarks = false,
            nativeSearch = true,         // some OPDS feeds support search
            collections = false,
            readlists = false,
            nativeSeries = false,        // OPDS is flat; we group by series metadata
        )
    }
}
