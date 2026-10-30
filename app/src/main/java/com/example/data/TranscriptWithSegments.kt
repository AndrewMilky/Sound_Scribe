package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

data class TranscriptWithSegments(
    @Embedded
    val transcript: TranscriptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transcriptId"
    )
    val segments: List<SegmentEntity> = emptyList()
)
