package com.koma.client.ui.reader.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.koma.client.R
import com.koma.client.domain.model.Bookmark

@Composable
fun ReaderOverlay(
    visible: Boolean,
    title: String,
    currentPage: Int,
    totalPages: Int,
    bookmarks: List<Bookmark>,
    brightness: Float,
    isOffline: Boolean = false,
    onBack: () -> Unit,
    onPageChange: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onBookmarksListClick: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrentPageBookmarked = bookmarks.any { it.page == currentPage }
    var showBrightness by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Top bar
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // Offline badge
                if (isOffline) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Offline", style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                // Bookmarks list button
                IconButton(onClick = onBookmarksListClick) {
                    Icon(Icons.AutoMirrored.Filled.List, "Bookmarks list", tint = Color.White)
                }
                // Add/remove bookmark for current page
                IconButton(onClick = onBookmarkClick) {
                    Icon(
                        ImageVector.vectorResource(
                            id = if (isCurrentPageBookmarked) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark,
                        ),
                        contentDescription = if (isCurrentPageBookmarked) "Remove bookmark" else "Add bookmark",
                        tint = if (isCurrentPageBookmarked) Color(0xFFFFD700) else Color.White,
                    )
                }
                // Brightness toggle
                TextButton(onClick = { showBrightness = !showBrightness }) {
                    Text(
                        "☀",
                        color = if (showBrightness) MaterialTheme.colorScheme.primary else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, "Settings", tint = Color.White)
                }
            }
        }

        // Bottom area
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Brightness slider (only shown when toggled)
                if (showBrightness) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("☀", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = if (brightness < 0f) 0.5f else brightness,
                            onValueChange = onBrightnessChange,
                            valueRange = 0.01f..1f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Page indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${currentPage + 1}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("$totalPages", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                if (totalPages > 1) {
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { onPageChange(it.toInt()) },
                        valueRange = 0f..(totalPages - 1).toFloat(),
                        steps = (totalPages - 2).coerceAtLeast(0),
                    )
                }
            }
        }
    }
}
