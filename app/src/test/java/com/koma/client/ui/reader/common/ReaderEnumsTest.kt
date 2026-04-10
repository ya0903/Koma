package com.koma.client.ui.reader.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderEnumsTest {

    @Test
    fun fitMode_has_all_variants() {
        assertThat(FitMode.entries.map { it.name }.toSet())
            .containsExactly("WIDTH", "HEIGHT", "SCREEN", "ORIGINAL")
    }

    @Test
    fun readingDirection_has_all_variants() {
        assertThat(ReadingDirection.entries.map { it.name }.toSet())
            .containsExactly("LTR", "RTL", "VERTICAL", "WEBTOON")
    }

    @Test
    fun pageLayout_has_all_variants() {
        assertThat(PageLayout.entries.map { it.name }.toSet())
            .containsExactly("SINGLE", "DOUBLE")
    }

    @Test
    fun readingDirection_isHorizontal() {
        assertThat(ReadingDirection.LTR.isHorizontal).isTrue()
        assertThat(ReadingDirection.RTL.isHorizontal).isTrue()
        assertThat(ReadingDirection.VERTICAL.isHorizontal).isFalse()
        assertThat(ReadingDirection.WEBTOON.isHorizontal).isFalse()
    }

    @Test
    fun readingDirection_isReversed() {
        assertThat(ReadingDirection.RTL.isReversed).isTrue()
        assertThat(ReadingDirection.LTR.isReversed).isFalse()
        assertThat(ReadingDirection.VERTICAL.isReversed).isFalse()
        assertThat(ReadingDirection.WEBTOON.isReversed).isFalse()
    }

    @Test
    fun readingDirection_isContinuousScroll() {
        assertThat(ReadingDirection.WEBTOON.isContinuousScroll).isTrue()
        assertThat(ReadingDirection.LTR.isContinuousScroll).isFalse()
        assertThat(ReadingDirection.RTL.isContinuousScroll).isFalse()
        assertThat(ReadingDirection.VERTICAL.isContinuousScroll).isFalse()
    }
}
