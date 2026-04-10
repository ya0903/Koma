package com.koma.client.domain.model

enum class DownloadState { QUEUED, DOWNLOADING, COMPLETE, FAILED }

data class Download(
    val bookId: String,
    val serverId: String,
    val bookTitle: String,
    val state: DownloadState,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val filePath: String? = null,
    val mediaType: String = "IMAGE",
    val error: String? = null,
)
