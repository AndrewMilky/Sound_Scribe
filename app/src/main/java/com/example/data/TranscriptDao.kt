package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {

    @Transaction
    @Query("SELECT * FROM transcripts ORDER BY createdAt DESC")
    fun getAllTranscripts(): Flow<List<TranscriptWithSegments>>

    @Transaction
    @Query("SELECT * FROM transcripts WHERE id = :id")
    fun getTranscriptById(id: Long): Flow<TranscriptWithSegments?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun deleteTranscriptById(id: Long)

    @Query("DELETE FROM transcripts")
    suspend fun deleteAllTranscripts()

    @Query("UPDATE transcripts SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE transcripts SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("SELECT COUNT(*) FROM transcripts")
    suspend fun getTranscriptCount(): Int
}
