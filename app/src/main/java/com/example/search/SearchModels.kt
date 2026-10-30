package com.example.search

enum class DatePreset(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    PAST_7_DAYS("Past 7 Days"),
    PAST_30_DAYS("Past 30 Days"),
    CUSTOM("Custom Range")
}

data class SearchFilterState(
    val query: String = "",
    val selectedCategories: Set<String> = emptySet(),
    val selectedFormats: Set<String> = emptySet(),
    val datePreset: DatePreset = DatePreset.ALL_TIME,
    val customStartDate: Long? = null,
    val customEndDate: Long? = null,
    val speakerQuery: String = "",
    val minDurationMinutes: Int = 0,
    val maxDurationMinutes: Int = 120
) {
    val isActiveFilter: Boolean
        get() = query.isNotBlank() ||
                selectedCategories.isNotEmpty() ||
                selectedFormats.isNotEmpty() ||
                datePreset != DatePreset.ALL_TIME ||
                speakerQuery.isNotBlank() ||
                minDurationMinutes > 0
}

data class SearchResultMatch(
    val transcriptId: Long,
    val transcriptTitle: String,
    val fileName: String,
    val format: String,
    val category: String,
    val createdAt: Long,
    val segmentIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val speaker: String,
    val text: String,
    val matchOffsets: List<Pair<Int, Int>> = emptyList() // start to end index of matches
)
