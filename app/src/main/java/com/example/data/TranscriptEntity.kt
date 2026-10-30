package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val fileName: String,
    val fileUri: String = "",
    val fileFormat: String, // MP3, WAV, FLAC, M4A
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val category: String = "Meeting", // Meeting, Interview, Lecture, Voice Memo, Legal, Podcast, Other
    val speakerCount: Int = 1,
    val status: String = "COMPLETED", // COMPLETED, PROCESSING, FAILED
    val summary: String = "",
    val fullText: String = "",
    val separateFilePath: String = "" // File path to the individual transcript file
)
