package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SegmentEntity
import com.example.data.TranscriptEntity
import com.example.data.TranscriptWithSegments
import com.example.search.DatePreset
import com.example.search.SearchFilterEngine
import com.example.search.SearchFilterState
import com.example.util.AudioTimeUtils
import com.example.util.TranscriptExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches AudioScribe`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AudioScribe", appName)
    }

    @Test
    fun `audio time formatting produces accurate timestamp format`() {
        assertEquals("00:00", AudioTimeUtils.formatDuration(0L))
        assertEquals("01:24", AudioTimeUtils.formatDuration(84000L))
        assertEquals("01:02:15", AudioTimeUtils.formatDuration(3735000L))
    }

    @Test
    fun `search engine finds keywords across all transcripts simultaneously`() {
        val transcript1 = TranscriptEntity(
            id = 1,
            title = "Engineering Architecture",
            fileName = "arch.wav",
            fileFormat = "WAV",
            category = "Meeting"
        )
        val seg1 = SegmentEntity(
            id = 1,
            transcriptId = 1,
            segmentIndex = 0,
            startTimeMs = 12000L,
            endTimeMs = 18000L,
            speaker = "Speaker 1",
            text = "We should optimize database search latency and full text indexes."
        )

        val transcript2 = TranscriptEntity(
            id = 2,
            title = "Legal Deposition",
            fileName = "legal.flac",
            fileFormat = "FLAC",
            category = "Legal"
        )
        val seg2 = SegmentEntity(
            id = 2,
            transcriptId = 2,
            segmentIndex = 0,
            startTimeMs = 24000L,
            endTimeMs = 30000L,
            speaker = "Attorney",
            text = "Did you review the timestamped audio files in the database?"
        )

        val allItems = listOf(
            TranscriptWithSegments(transcript1, listOf(seg1)),
            TranscriptWithSegments(transcript2, listOf(seg2))
        )

        // Query for "database" across all files at once
        val results = SearchFilterEngine.performSearch(
            allItems,
            SearchFilterState(query = "database")
        )

        assertEquals(2, results.size)
        assertEquals(1L, results[0].transcriptId)
        assertEquals(12000L, results[0].startTimeMs)
        assertEquals(2L, results[1].transcriptId)
        assertEquals(24000L, results[1].startTimeMs)
    }

    @Test
    fun `search filter finds Jesus in sermon transcripts with whitespace tolerance`() {
        val transcript = TranscriptEntity(
            id = 5,
            title = "Acts 2 - The Holy Ghost",
            fileName = "acts2.m4a",
            fileFormat = "M4A",
            category = "Ministry"
        )
        val seg = SegmentEntity(
            id = 50,
            transcriptId = 5,
            segmentIndex = 1,
            startTimeMs = 15000L,
            endTimeMs = 25000L,
            speaker = "Pastor Paul",
            text = "In this teaching we focus upon the lordship of Jesus Christ."
        )

        val allItems = listOf(TranscriptWithSegments(transcript, listOf(seg)))

        val results = SearchFilterEngine.performSearch(
            allItems,
            SearchFilterState(query = "Jesus ")
        )

        assertEquals(1, results.size)
        assertEquals("Pastor Paul", results[0].speaker)
        assertTrue(results[0].text.contains("Jesus"))
    }

    @Test
    fun `pdf export excludes transcripts with no matching segments when searching`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transcriptWithMatch = TranscriptEntity(
            id = 1,
            title = "Ministry Teaching",
            fileName = "teaching.m4a",
            fileFormat = "M4A",
            category = "Ministry"
        )
        val matchSeg = SegmentEntity(
            id = 1,
            transcriptId = 1,
            segmentIndex = 0,
            startTimeMs = 1000L,
            endTimeMs = 12000L,
            speaker = "Pastor Paul",
            text = "Walking in faith and following Jesus."
        )

        val transcriptNoMatch = TranscriptEntity(
            id = 2,
            title = "Server Infrastructure",
            fileName = "server.mp3",
            fileFormat = "MP3",
            category = "Meeting"
        )
        val noMatchSeg = SegmentEntity(
            id = 2,
            transcriptId = 2,
            segmentIndex = 0,
            startTimeMs = 1000L,
            endTimeMs = 12000L,
            speaker = "Dev",
            text = "Deploying kubernetes cluster."
        )

        val allItems = listOf(
            TranscriptWithSegments(transcriptWithMatch, listOf(matchSeg)),
            TranscriptWithSegments(transcriptNoMatch, listOf(noMatchSeg))
        )

        val pdfFile = TranscriptExporter.exportToPdf(
            context,
            allItems,
            "Jesus"
        )

        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
    }

    @Test
    fun `csv export creates non-empty valid file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transcript = TranscriptEntity(
            id = 10,
            title = "Test Meeting",
            fileName = "meeting.m4a",
            fileFormat = "M4A",
            category = "Meeting"
        )
        val seg = SegmentEntity(
            id = 10,
            transcriptId = 10,
            segmentIndex = 0,
            startTimeMs = 0L,
            endTimeMs = 15000L,
            speaker = "Speaker 1",
            text = "Meeting started at 9 AM."
        )

        val file = TranscriptExporter.exportToCsv(
            context,
            listOf(TranscriptWithSegments(transcript, listOf(seg))),
            "Meeting"
        )

        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        val content = file.readText()
        assertTrue(content.contains("Test Meeting"))
        assertTrue(content.contains("meeting.m4a"))
        assertTrue(content.contains("Meeting started at 9 AM."))
    }
}
