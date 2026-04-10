package com.koma.client.ui.nav

object Routes {
    const val HOME = "home"
    const val ADD_SERVER = "add_server"
    const val MAIN = "main"
    const val IMAGE_READER = "image_reader/{bookId}"
    const val EPUB_READER = "epub_reader/{bookId}"

    fun imageReader(bookId: String) = "image_reader/$bookId"
    fun epubReader(bookId: String) = "epub_reader/$bookId"
}
