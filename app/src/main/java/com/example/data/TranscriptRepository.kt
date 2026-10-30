package com.example.data

import kotlinx.coroutines.flow.Flow

class TranscriptRepository(private val transcriptDao: TranscriptDao) {

    val allTranscripts: Flow<List<TranscriptWithSegments>> = transcriptDao.getAllTranscripts()

    fun getTranscriptById(id: Long): Flow<TranscriptWithSegments?> {
        return transcriptDao.getTranscriptById(id)
    }

    suspend fun saveTranscript(
        transcript: TranscriptEntity,
        segments: List<SegmentEntity>
    ): Long {
        val transcriptId = transcriptDao.insertTranscript(transcript)
        if (segments.isNotEmpty()) {
            val assignedSegments = segments.map { it.copy(transcriptId = transcriptId) }
            transcriptDao.insertSegments(assignedSegments)
        }
        return transcriptId
    }

    suspend fun deleteTranscript(id: Long) {
        transcriptDao.deleteTranscriptById(id)
    }

    suspend fun deleteAll() {
        transcriptDao.deleteAllTranscripts()
    }

    suspend fun updateCategory(id: Long, category: String) {
        transcriptDao.updateCategory(id, category)
    }

    suspend fun updateTitle(id: Long, title: String) {
        transcriptDao.updateTitle(id, title)
    }
}
