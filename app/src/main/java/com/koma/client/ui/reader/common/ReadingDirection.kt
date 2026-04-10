package com.koma.client.ui.reader.common

enum class ReadingDirection {
    LTR, RTL, VERTICAL;

    val isHorizontal: Boolean get() = this != VERTICAL
    val isReversed: Boolean get() = this == RTL
}
