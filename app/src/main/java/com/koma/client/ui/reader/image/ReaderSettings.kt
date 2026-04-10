package com.koma.client.ui.reader.image

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koma.client.ui.reader.common.FitMode
import com.koma.client.ui.reader.common.PageLayout
import com.koma.client.ui.reader.common.ReadingDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    fitMode: FitMode,
    readingDirection: ReadingDirection,
    pageLayout: PageLayout,
    keepScreenOn: Boolean,
    onFitModeChange: (FitMode) -> Unit,
    onDirectionChange: (ReadingDirection) -> Unit,
    onLayoutChange: (PageLayout) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Reader Settings", style = MaterialTheme.typography.titleMedium)

            // Fit mode
            Text("Fit Mode", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitMode.entries.forEach { mode ->
                    FilterChip(
                        selected = fitMode == mode,
                        onClick = { onFitModeChange(mode) },
                        label = { Text(mode.name) },
                    )
                }
            }

            // Reading direction
            Text("Reading Direction", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = readingDirection == dir,
                        onClick = { onDirectionChange(dir) },
                        label = { Text(dir.name) },
                    )
                }
            }

            // Page layout
            Text("Page Layout", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = pageLayout == layout,
                        onClick = { onLayoutChange(layout) },
                        label = { Text(layout.name) },
                    )
                }
            }

            // Keep screen on
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Keep Screen On")
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
