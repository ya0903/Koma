package com.koma.client.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class SortOption(val label: String, val apiSort: String) {
    TITLE_ASC("Title (A-Z)", "metadata.titleSort,asc"),
    TITLE_DESC("Title (Z-A)", "metadata.titleSort,desc"),
    DATE_ADDED_NEW("Date Added (Newest)", "created,desc"),
    DATE_ADDED_OLD("Date Added (Oldest)", "created,asc"),
    RELEASE_DATE("Release Date", "booksMetadata.releaseDate,desc"),
    LAST_UPDATED("Last Updated", "lastModified,desc"),
}

data class SearchFilters(
    val query: String = "",
    val sortBy: SortOption = SortOption.TITLE_ASC,
    val status: String? = null,
    val readStatus: String? = null,
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val publishers: Set<String> = emptySet(),
) {
    val activeFilterCount: Int
        get() = listOfNotNull(
            if (sortBy != SortOption.TITLE_ASC) 1 else null,
            if (status != null) 1 else null,
            if (readStatus != null) 1 else null,
            if (genres.isNotEmpty()) 1 else null,
            if (tags.isNotEmpty()) 1 else null,
            if (publishers.isNotEmpty()) 1 else null,
        ).size
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    filters: SearchFilters,
    availableGenres: List<String>,
    availableTags: List<String>,
    availablePublishers: List<String>,
    sheetState: SheetState,
    onFiltersChanged: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Filter & Sort",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Sort By
            FilterSection(title = "Sort By") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortOption.entries.forEach { option ->
                        FilterChip(
                            selected = filters.sortBy == option,
                            onClick = { onFiltersChanged(filters.copy(sortBy = option)) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Status
            FilterSection(title = "Status") {
                val statuses = listOf(null to "All", "ONGOING" to "Ongoing", "ENDED" to "Ended", "HIATUS" to "Hiatus", "ABANDONED" to "Abandoned")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    statuses.forEach { (value, label) ->
                        FilterChip(
                            selected = filters.status == value,
                            onClick = { onFiltersChanged(filters.copy(status = value)) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Read Status
            FilterSection(title = "Read Status") {
                val readStatuses = listOf(null to "All", "UNREAD" to "Unread", "IN_PROGRESS" to "In Progress", "READ" to "Read")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    readStatuses.forEach { (value, label) ->
                        FilterChip(
                            selected = filters.readStatus == value,
                            onClick = { onFiltersChanged(filters.copy(readStatus = value)) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Genres
            if (availableGenres.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FilterSection(title = "Genres", initiallyExpanded = false) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableGenres.forEach { genre ->
                            val selected = genre in filters.genres
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) filters.genres - genre else filters.genres + genre
                                    onFiltersChanged(filters.copy(genres = updated))
                                },
                                label = { Text(genre) },
                            )
                        }
                    }
                }
            }

            // Tags
            if (availableTags.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FilterSection(title = "Tags") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableTags.forEach { tag ->
                            val selected = tag in filters.tags
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) filters.tags - tag else filters.tags + tag
                                    onFiltersChanged(filters.copy(tags = updated))
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }

            // Publishers
            if (availablePublishers.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                FilterSection(title = "Publishers / Writers", initiallyExpanded = false) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availablePublishers.forEach { publisher ->
                            val selected = publisher in filters.publishers
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) filters.publishers - publisher else filters.publishers + publisher
                                    onFiltersChanged(filters.copy(publishers = updated))
                                },
                                label = { Text(publisher) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onFiltersChanged(SearchFilters(query = filters.query))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Clear All")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}
