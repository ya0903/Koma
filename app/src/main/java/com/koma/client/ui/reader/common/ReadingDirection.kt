package com.koma.client.ui.reader.common

enum class ReadingDirection {
    LTR, RTL, VERTICAL, WEBTOON;

    val isHorizontal: Boolean get() = this == LTR || this == RTL
    val isReversed: Boolean get() = this == RTL
    val isContinuousScroll: Boolean get() = this == WEBTOON
}
