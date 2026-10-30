package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transcriptId"])]
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transcriptId: Long,
    val segmentIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val speaker: String = "Speaker 1",
    val text: String
)
