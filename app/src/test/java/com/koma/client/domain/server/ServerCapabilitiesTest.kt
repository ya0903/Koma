package com.koma.client.domain.server

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerCapabilitiesTest {

    @Test
    fun komga_defaults_support_progress_and_series() {
        val caps = ServerCapabilities.komgaDefaults()
        assertThat(caps.serverProgressSync).isTrue()
        assertThat(caps.nativeSeries).isTrue()
        assertThat(caps.nativeSearch).isTrue()
        assertThat(caps.serverBookmarks).isFalse() // v1: bookmarks local only
    }

    @Test
    fun kavita_defaults_support_progress_and_series() {
        val caps = ServerCapabilities.kavitaDefaults()
        assertThat(caps.serverProgressSync).isTrue()
        assertThat(caps.nativeSeries).isTrue()
    }

    @Test
    fun calibreWeb_defaults_no_progress_no_native_series() {
        val caps = ServerCapabilities.calibreWebDefaults()
        assertThat(caps.serverProgressSync).isFalse()
        assertThat(caps.nativeSeries).isFalse()
        assertThat(caps.nativeSearch).isTrue()
    }

    @Test
    fun mediaServerType_has_all_three_variants() {
        assertThat(MediaServerType.values().toSet())
            .containsExactly(MediaServerType.KOMGA, MediaServerType.KAVITA, MediaServerType.CALIBRE_WEB)
    }
}
