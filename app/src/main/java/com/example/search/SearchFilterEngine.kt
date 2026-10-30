package com.example.search

import com.example.data.TranscriptWithSegments
import java.util.Calendar

object SearchFilterEngine {

    /**
     * Filters all transcripts simultaneously and returns highlighted matching segment hits.
     */
    fun performSearch(
        allTranscripts: List<TranscriptWithSegments>,
        filter: SearchFilterState
    ): List<SearchResultMatch> {
        val matches = mutableListOf<SearchResultMatch>()

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        val dateMinThreshold: Long? = when (filter.datePreset) {
            DatePreset.ALL_TIME -> null
            DatePreset.TODAY -> {
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            DatePreset.PAST_7_DAYS -> now - (7L * 24 * 60 * 60 * 1000)
            DatePreset.PAST_30_DAYS -> now - (30L * 24 * 60 * 60 * 1000)
            DatePreset.CUSTOM -> filter.customStartDate
        }

        val dateMaxThreshold: Long? = when (filter.datePreset) {
            DatePreset.CUSTOM -> filter.customEndDate
            else -> null
        }

        val cleanQuery = filter.query.trim()
        val queryTerms = if (cleanQuery.isBlank()) emptyList() else cleanQuery.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        for (item in allTranscripts) {
            val t = item.transcript

            // 1. Filter Category
            if (filter.selectedCategories.isNotEmpty() && !filter.selectedCategories.contains(t.category)) {
                continue
            }

            // 2. Filter Format (MP3, WAV, FLAC, M4A)
            if (filter.selectedFormats.isNotEmpty() && !filter.selectedFormats.contains(t.fileFormat)) {
                continue
            }

            // 3. Filter Date Range
            if (dateMinThreshold != null && t.createdAt < dateMinThreshold) {
                continue
            }
            if (dateMaxThreshold != null && t.createdAt > dateMaxThreshold) {
                continue
            }

            // 4. Filter Duration
            val durationMinutes = (t.durationMs / 60000).toInt()
            if (durationMinutes < filter.minDurationMinutes) {
                continue
            }

            // 5. Filter Speaker
            val speakerQuery = filter.speakerQuery.trim().lowercase()

            val titleMatches = queryTerms.isNotEmpty() && queryTerms.all { "${t.title} ${t.fileName} ${t.summary}".lowercase().contains(it) }

            for (seg in item.segments) {
                if (speakerQuery.isNotBlank() && !seg.speaker.lowercase().contains(speakerQuery)) {
                    continue
                }

                if (queryTerms.isEmpty()) {
                    // Include all segments matching the metadata/date criteria
                    matches.add(
                        SearchResultMatch(
                            transcriptId = t.id,
                            transcriptTitle = t.title,
                            fileName = t.fileName,
                            format = t.fileFormat,
                            category = t.category,
                            createdAt = t.createdAt,
                            segmentIndex = seg.segmentIndex,
                            startTimeMs = seg.startTimeMs,
                            endTimeMs = seg.endTimeMs,
                            speaker = seg.speaker,
                            text = seg.text,
                            matchOffsets = emptyList()
                        )
                    )
                } else {
                    val lowerText = seg.text.lowercase()
                    val lowerCorpus = "${seg.text} ${seg.speaker} ${t.title}".lowercase()
                    val textContainsAll = queryTerms.all { lowerText.contains(it) }
                    val corpusContainsAll = queryTerms.all { lowerCorpus.contains(it) }

                    if (textContainsAll || (titleMatches && (seg.segmentIndex <= 3 || corpusContainsAll))) {
                        val offsets = mutableListOf<Pair<Int, Int>>()
                        for (term in queryTerms) {
                            var startIdx = 0
                            while (startIdx < seg.text.length) {
                                val found = lowerText.indexOf(term, startIdx)
                                if (found == -1) break
                                offsets.add(Pair(found, found + term.length))
                                startIdx = found + term.length
                            }
                        }

                        matches.add(
                            SearchResultMatch(
                                transcriptId = t.id,
                                transcriptTitle = t.title,
                                fileName = t.fileName,
                                format = t.fileFormat,
                                category = t.category,
                                createdAt = t.createdAt,
                                segmentIndex = seg.segmentIndex,
                                startTimeMs = seg.startTimeMs,
                                endTimeMs = seg.endTimeMs,
                                speaker = seg.speaker,
                                text = seg.text,
                                matchOffsets = offsets
                            )
                        )
                    }
                }
            }
        }

        return matches
    }
}
