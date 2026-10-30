package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TranscriptRepository
import com.example.data.TranscriptWithSegments
import com.example.search.DatePreset
import com.example.search.SearchFilterEngine
import com.example.search.SearchFilterState
import com.example.search.SearchResultMatch
import com.example.service.AudioTranscriptionService
import com.example.service.TranscriptionProgress
import com.example.util.TranscriptExporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class AppTab {
    DASHBOARD,
    SEARCH,
    BATCH_UPLOAD,
    DETAIL
}

class AudioScribeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = TranscriptRepository(database.transcriptDao())
    private val transcriptionService = AudioTranscriptionService(application)

    val allTranscripts: StateFlow<List<TranscriptWithSegments>> = repository.allTranscripts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _selectedTranscriptId = MutableStateFlow<Long?>(null)
    val selectedTranscriptId: StateFlow<Long?> = _selectedTranscriptId.asStateFlow()

    private val _jumpToTimestampMs = MutableStateFlow<Long?>(null)
    val jumpToTimestampMs: StateFlow<Long?> = _jumpToTimestampMs.asStateFlow()

    private val _searchFilterState = MutableStateFlow(SearchFilterState())
    val searchFilterState: StateFlow<SearchFilterState> = _searchFilterState.asStateFlow()

    private val _transcriptionProgress = MutableStateFlow(TranscriptionProgress())
    val transcriptionProgress: StateFlow<TranscriptionProgress> = _transcriptionProgress.asStateFlow()

    private val _exportedFileResult = MutableStateFlow<File?>(null)
    val exportedFileResult: StateFlow<File?> = _exportedFileResult.asStateFlow()

    private var bulkJob: Job? = null

    // Search results reactively computed whenever transcripts or filter change
    val searchResults: StateFlow<List<SearchResultMatch>> = combine(
        allTranscripts,
        _searchFilterState
    ) { transcripts, filter ->
        SearchFilterEngine.performSearch(transcripts, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Pre-seed with high quality demo audio files on very first run if database is empty
        viewModelScope.launch {
            delay(400)
            if (database.transcriptDao().getTranscriptCount() == 0) {
                startDemoBatchTranscription(count = 5)
            }
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun openTranscriptDetail(transcriptId: Long, seekTimestampMs: Long? = null) {
        _selectedTranscriptId.value = transcriptId
        _jumpToTimestampMs.value = seekTimestampMs
        _currentTab.value = AppTab.DETAIL
    }

    fun clearJumpTimestamp() {
        _jumpToTimestampMs.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchFilterState.value = _searchFilterState.value.copy(query = query)
    }

    fun toggleCategoryFilter(category: String) {
        val current = _searchFilterState.value.selectedCategories.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _searchFilterState.value = _searchFilterState.value.copy(selectedCategories = current)
    }

    fun toggleFormatFilter(format: String) {
        val current = _searchFilterState.value.selectedFormats.toMutableSet()
        if (current.contains(format)) {
            current.remove(format)
        } else {
            current.add(format)
        }
        _searchFilterState.value = _searchFilterState.value.copy(selectedFormats = current)
    }

    fun setDatePreset(preset: DatePreset) {
        _searchFilterState.value = _searchFilterState.value.copy(datePreset = preset)
    }

    fun setCustomDateRange(startMs: Long?, endMs: Long?) {
        _searchFilterState.value = _searchFilterState.value.copy(
            datePreset = DatePreset.CUSTOM,
            customStartDate = startMs,
            customEndDate = endMs
        )
    }

    fun setSpeakerFilter(speaker: String) {
        _searchFilterState.value = _searchFilterState.value.copy(speakerQuery = speaker)
    }

    fun setMinDurationFilter(minutes: Int) {
        _searchFilterState.value = _searchFilterState.value.copy(minDurationMinutes = minutes)
    }

    fun resetFilters() {
        _searchFilterState.value = SearchFilterState(query = _searchFilterState.value.query)
    }

    /**
     * Bulk transcription for up to 100+ selected audio files.
     */
    fun startBulkTranscription(uris: List<Uri>) {
        if (uris.isEmpty()) return

        bulkJob?.cancel()
        bulkJob = viewModelScope.launch {
            val total = uris.size
            _transcriptionProgress.value = TranscriptionProgress(
                isRunning = true,
                totalCount = total,
                completedCount = 0,
                statusMessage = "Analyzing metadata for $total files..."
            )

            for ((index, uri) in uris.withIndex()) {
                val audioInfo = transcriptionService.resolveAudioFile(uri)

                _transcriptionProgress.value = TranscriptionProgress(
                    isRunning = true,
                    totalCount = total,
                    completedCount = index,
                    currentFileName = audioInfo.name,
                    currentProgressPercent = index.toFloat() / total,
                    statusMessage = "Transcribing file ${index + 1} of $total: ${audioInfo.name} [${audioInfo.format}]"
                )

                // Transcribe into timestamped segments
                val (transcript, segments) = transcriptionService.transcribeAudio(audioInfo)

                // Save to Room database
                val savedId = repository.saveTranscript(transcript, segments)

                // Write separate individual transcript file on disk
                val separateFilePath = transcriptionService.writeIndividualTranscriptFiles(
                    savedId,
                    transcript.copy(id = savedId),
                    segments
                )

                // Update transcript with its individual file path
                database.transcriptDao().insertTranscript(
                    transcript.copy(id = savedId, separateFilePath = separateFilePath)
                )

                // Small realistic yield for UI responsiveness
                delay(120)
            }

            _transcriptionProgress.value = TranscriptionProgress(
                isRunning = false,
                totalCount = total,
                completedCount = total,
                currentProgressPercent = 1f,
                statusMessage = "Batch complete! Successfully transcribed $total audio files."
            )
        }
    }

    /**
     * Generates and processes a sample audio batch (e.g. 5, 25, or 100 audio files).
     */
    fun startDemoBatchTranscription(count: Int) {
        bulkJob?.cancel()
        bulkJob = viewModelScope.launch {
            _transcriptionProgress.value = TranscriptionProgress(
                isRunning = true,
                totalCount = count,
                completedCount = 0,
                statusMessage = "Preparing $count sample audio files (MP3, WAV, FLAC, M4A)..."
            )

            val sampleFiles = transcriptionService.generateSampleAudioBatch(count)

            for ((index, info) in sampleFiles.withIndex()) {
                _transcriptionProgress.value = TranscriptionProgress(
                    isRunning = true,
                    totalCount = count,
                    completedCount = index,
                    currentFileName = info.name,
                    currentProgressPercent = index.toFloat() / count,
                    statusMessage = "Transcribing [${info.format}] ${index + 1}/$count: ${info.name}"
                )

                val (transcript, segments) = transcriptionService.transcribeAudio(info)
                val savedId = repository.saveTranscript(transcript, segments)

                val separatePath = transcriptionService.writeIndividualTranscriptFiles(
                    savedId,
                    transcript.copy(id = savedId),
                    segments
                )

                database.transcriptDao().insertTranscript(
                    transcript.copy(id = savedId, separateFilePath = separatePath)
                )

                delay(80)
            }

            _transcriptionProgress.value = TranscriptionProgress(
                isRunning = false,
                totalCount = count,
                completedCount = count,
                currentProgressPercent = 1f,
                statusMessage = "Successfully completed batch analysis of $count audio files!"
            )
        }
    }

    /**
     * Re-runs the full-duration transcription engine across all existing audio files in the database.
     */
    fun retranscribeAllExistingTranscripts() {
        bulkJob?.cancel()
        bulkJob = viewModelScope.launch {
            val list = allTranscripts.value
            val total = list.size
            if (total == 0) return@launch

            _transcriptionProgress.value = TranscriptionProgress(
                isRunning = true,
                totalCount = total,
                completedCount = 0,
                statusMessage = "Re-transcribing $total audio files with full-duration engine..."
            )

            for ((index, item) in list.withIndex()) {
                val t = item.transcript
                val uri = if (t.fileUri.isNotBlank()) Uri.parse(t.fileUri) else Uri.EMPTY
                val audioInfo = com.example.service.AudioFileInfo(
                    uri = uri,
                    name = t.fileName,
                    sizeBytes = t.fileSizeBytes,
                    format = t.fileFormat,
                    durationMs = t.durationMs
                )

                _transcriptionProgress.value = TranscriptionProgress(
                    isRunning = true,
                    totalCount = total,
                    completedCount = index,
                    currentFileName = t.fileName,
                    currentProgressPercent = index.toFloat() / total,
                    statusMessage = "Analyzing & indexing: ${t.title} (${index + 1}/$total)"
                )

                val (newTranscript, newSegments) = transcriptionService.transcribeAudio(audioInfo)
                val separatePath = transcriptionService.writeIndividualTranscriptFiles(
                    t.id,
                    newTranscript.copy(id = t.id),
                    newSegments
                )

                database.transcriptDao().insertTranscript(
                    newTranscript.copy(id = t.id, separateFilePath = separatePath)
                )
                database.transcriptDao().insertSegments(
                    newSegments.map { it.copy(transcriptId = t.id) }
                )

                delay(30)
            }

            _transcriptionProgress.value = TranscriptionProgress(
                isRunning = false,
                totalCount = total,
                completedCount = total,
                currentProgressPercent = 1f,
                statusMessage = "Successfully re-transcribed all $total audio files with full timestamps!"
            )
        }
    }

    fun cancelBulkProcessing() {
        bulkJob?.cancel()
        _transcriptionProgress.value = TranscriptionProgress(
            isRunning = false,
            statusMessage = "Bulk processing cancelled."
        )
    }

    fun exportResultsAsCsv(context: Context): File {
        val filtered = getFilteredTranscriptsForExport()
        val file = TranscriptExporter.exportToCsv(
            context,
            filtered,
            _searchFilterState.value.query
        )
        _exportedFileResult.value = file
        return file
    }

    fun exportResultsAsPdf(context: Context): File {
        val filtered = getFilteredTranscriptsForExport()
        val file = TranscriptExporter.exportToPdf(
            context,
            filtered,
            _searchFilterState.value.query
        )
        _exportedFileResult.value = file
        return file
    }

    fun exportSingleTranscriptPdf(context: Context, item: TranscriptWithSegments): File {
        val file = TranscriptExporter.exportToPdf(context, listOf(item), "")
        _exportedFileResult.value = file
        return file
    }

    fun exportSingleTranscriptCsv(context: Context, item: TranscriptWithSegments): File {
        val file = TranscriptExporter.exportToCsv(context, listOf(item), "")
        _exportedFileResult.value = file
        return file
    }

    fun clearExportResult() {
        _exportedFileResult.value = null
    }

    fun deleteTranscript(id: Long) {
        viewModelScope.launch {
            repository.deleteTranscript(id)
            if (_selectedTranscriptId.value == id) {
                _selectedTranscriptId.value = null
                _currentTab.value = AppTab.DASHBOARD
            }
        }
    }

    fun clearAllTranscripts() {
        viewModelScope.launch {
            repository.deleteAll()
            _selectedTranscriptId.value = null
            _currentTab.value = AppTab.DASHBOARD
        }
    }

    fun updateTranscriptCategory(id: Long, category: String) {
        viewModelScope.launch {
            repository.updateCategory(id, category)
        }
    }

    fun updateTranscriptTitle(id: Long, title: String) {
        viewModelScope.launch {
            repository.updateTitle(id, title)
        }
    }

    private fun getFilteredTranscriptsForExport(): List<TranscriptWithSegments> {
        val query = _searchFilterState.value.query.trim()
        val matches = searchResults.value
        val matchedIds = matches.map { it.transcriptId }.toSet()
        return if (query.isNotBlank() || _searchFilterState.value.isActiveFilter) {
            allTranscripts.value.filter { matchedIds.contains(it.transcript.id) }
        } else {
            allTranscripts.value
        }
    }
}
