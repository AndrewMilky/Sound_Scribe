package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.search.DatePreset
import com.example.search.SearchFilterState
import com.example.search.SearchResultMatch
import com.example.ui.components.AdvancedSearchFilterDialog
import com.example.ui.components.ExportOptionsDialog
import com.example.ui.components.HighlightedText
import com.example.util.AudioTimeUtils
import com.example.util.ExportShareHelper
import com.example.viewmodel.AudioScribeViewModel

@Composable
fun SearchScreen(
    viewModel: AudioScribeViewModel,
    filterState: SearchFilterState,
    searchResults: List<SearchResultMatch>,
    onJumpToTimestamp: (Long, Long) -> Unit // transcriptId, timestampMs
) {
    val context = LocalContext.current
    var showFilterDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val activeFilterCount = remember(filterState) {
        var count = 0
        if (filterState.selectedCategories.isNotEmpty()) count++
        if (filterState.selectedFormats.isNotEmpty()) count++
        if (filterState.datePreset != DatePreset.ALL_TIME) count++
        if (filterState.speakerQuery.isNotBlank()) count++
        if (filterState.minDurationMinutes > 0) count++
        count
    }

    val matchedFileCount = remember(searchResults) {
        searchResults.map { it.transcriptId }.distinct().size
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        // Top Search Bar & Filter Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = filterState.query,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("Search all transcripts at once...") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (filterState.query.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.updateSearchQuery("") },
                                    modifier = Modifier.testTag("clear_search_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("global_search_input"),
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Filter Button with Badge
                    BadgedBox(
                        badge = {
                            if (activeFilterCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("$activeFilterCount")
                                }
                            }
                        }
                    ) {
                        Surface(
                            onClick = { showFilterDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = if (activeFilterCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("open_filter_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FilterAlt,
                                    contentDescription = "Filters",
                                    tint = if (activeFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Active Filters Chips Row
                if (filterState.isActiveFilter) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (filterState.datePreset != DatePreset.ALL_TIME) {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.setDatePreset(DatePreset.ALL_TIME) },
                                label = { Text("Date: ${filterState.datePreset.label}", fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        filterState.selectedCategories.forEach { cat ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.toggleCategoryFilter(cat) },
                                label = { Text(cat, fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        filterState.selectedFormats.forEach { fmt ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.toggleFormatFilter(fmt) },
                                label = { Text(fmt, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        if (filterState.speakerQuery.isNotBlank()) {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.setSpeakerFilter("") },
                                label = { Text("Speaker: ${filterState.speakerQuery}", fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        if (filterState.minDurationMinutes > 0) {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.setMinDurationFilter(0) },
                                label = { Text("${filterState.minDurationMinutes}m+ duration", fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        Text(
                            text = "Clear All",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.resetFilters() }
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // Match Statistics & Export Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (filterState.query.isBlank()) {
                    "Showing all ${searchResults.size} segments in $matchedFileCount files"
                } else {
                    "${searchResults.size} matches found across $matchedFileCount files"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (searchResults.isNotEmpty()) {
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.testTag("export_search_results_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Results", fontSize = 12.sp)
                }
            }
        }

        // Search Results List
        if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No matching transcript segments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try adjusting your keyword query, date ranges, or category filters.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { viewModel.resetFilters() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset All Filters")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(searchResults, key = { "${it.transcriptId}_${it.segmentIndex}" }) { match ->
                    SearchResultCard(
                        match = match,
                        query = filterState.query,
                        onJump = { onJumpToTimestamp(match.transcriptId, match.startTimeMs) }
                    )
                }
            }
        }
    }

    // Advanced Filter Modal
    if (showFilterDialog) {
        AdvancedSearchFilterDialog(
            currentState = filterState,
            onDismiss = { showFilterDialog = false },
            onApply = { updatedState ->
                viewModel.updateSearchQuery(updatedState.query)
                if (filterState.selectedCategories != updatedState.selectedCategories) {
                    updatedState.selectedCategories.forEach { if (!filterState.selectedCategories.contains(it)) viewModel.toggleCategoryFilter(it) }
                    filterState.selectedCategories.forEach { if (!updatedState.selectedCategories.contains(it)) viewModel.toggleCategoryFilter(it) }
                }
                if (filterState.selectedFormats != updatedState.selectedFormats) {
                    updatedState.selectedFormats.forEach { if (!filterState.selectedFormats.contains(it)) viewModel.toggleFormatFilter(it) }
                    filterState.selectedFormats.forEach { if (!updatedState.selectedFormats.contains(it)) viewModel.toggleFormatFilter(it) }
                }
                viewModel.setDatePreset(updatedState.datePreset)
                viewModel.setSpeakerFilter(updatedState.speakerQuery)
                viewModel.setMinDurationFilter(updatedState.minDurationMinutes)
            },
            onReset = { viewModel.resetFilters() }
        )
    }

    // Export Options Modal for current search results
    if (showExportDialog) {
        ExportOptionsDialog(
            itemCount = searchResults.size,
            subtitle = if (filterState.query.isBlank()) "Filtered Search Results" else "Query: \"${filterState.query}\"",
            onDismiss = { showExportDialog = false },
            onExportPdf = {
                val file = viewModel.exportResultsAsPdf(context)
                ExportShareHelper.shareFile(context, file, "application/pdf", "Share Search Results PDF")
            },
            onExportCsv = {
                val file = viewModel.exportResultsAsCsv(context)
                ExportShareHelper.shareFile(context, file, "text/csv", "Share Search Results CSV")
            }
        )
    }
}

@Composable
fun SearchResultCard(
    match: SearchResultMatch,
    query: String,
    onJump: () -> Unit
) {
    val formatColor = when (match.format) {
        "MP3" -> Color(0xFF0284C7)
        "WAV" -> Color(0xFF10B981)
        "FLAC" -> Color(0xFF8B5CF6)
        "M4A" -> Color(0xFFF59E0B)
        else -> Color(0xFF06B6D4)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJump)
            .testTag("search_result_${match.transcriptId}_${match.segmentIndex}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Audio File Title, Format, Category
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = formatColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = match.format,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = formatColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = match.transcriptTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = AudioTimeUtils.formatDateShort(match.createdAt),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp Badge & Speaker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = onJump,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Jump",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${AudioTimeUtils.formatDuration(match.startTimeMs)} - ${AudioTimeUtils.formatDuration(match.endTimeMs)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = match.speaker,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Highlighted Segment Text
            HighlightedText(
                text = match.text,
                highlightQuery = query,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
