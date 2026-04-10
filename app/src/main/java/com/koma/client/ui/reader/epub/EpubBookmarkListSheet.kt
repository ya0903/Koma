package com.koma.client.ui.reader.epub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.koma.client.R
import com.koma.client.data.reader.EpubChapter
import com.koma.client.domain.model.Bookmark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubBookmarkListSheet(
    bookmarks: List<Bookmark>,
    chapters: List<EpubChapter>,
    onBookmarkClick: (Bookmark) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No bookmarks yet.\nTap the bookmark icon while reading to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        EpubBookmarkItem(
                            bookmark = bookmark,
                            chapterTitle = bookmark.page?.let { chapters.getOrNull(it)?.title },
                            onClick = { onBookmarkClick(bookmark) },
                            onDelete = { onDeleteBookmark(bookmark.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubBookmarkItem(
    bookmark: Bookmark,
    chapterTitle: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(bookmark.createdAtEpochMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            .format(Date(bookmark.createdAtEpochMs))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            ImageVector.vectorResource(id = R.drawable.ic_bookmark_fill),
            contentDescription = null,
            tint = Color(0xFFFFD700),
            modifier = Modifier
                .padding(end = 12.dp)
                .size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            val label = chapterTitle
                ?: bookmark.locator
                ?: bookmark.page?.let { "Chapter ${it + 1}" }
                ?: "Bookmark"
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (!bookmark.note.isNullOrBlank()) {
                Text(
                    bookmark.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
        }
    }
}
