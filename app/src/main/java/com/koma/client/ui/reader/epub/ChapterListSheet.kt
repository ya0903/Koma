package com.koma.client.ui.reader.epub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koma.client.data.reader.EpubChapter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListSheet(
    chapters: List<EpubChapter>,
    currentChapter: Int,
    onChapterSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "Chapters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val isSelected = index == currentChapter
                ListItem(
                    headlineContent = {
                        Text(
                            chapter.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    colors = if (isSelected) {
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable { onChapterSelect(index) },
                )
            }
        }
    }
}
