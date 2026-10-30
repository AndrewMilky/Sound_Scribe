package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.TranscriptWithSegments
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TranscriptExporter {

    /**
     * Exports transcripts or search matches to a CSV file.
     */
    fun exportToCsv(
        context: Context,
        transcripts: List<TranscriptWithSegments>,
        filterQuery: String = ""
    ): File {
        val cleanQuery = filterQuery.trim()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (cleanQuery.isBlank()) {
            "AudioScribe_Export_$timestamp.csv"
        } else {
            "AudioScribe_Search_${cleanQuery.replace("\\W+".toRegex(), "_")}_$timestamp.csv"
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val csvFile = File(exportDir, fileName)

        FileOutputStream(csvFile).bufferedWriter().use { writer ->
            writer.write("Transcript ID,Title,File Name,Format,Category,Date,Start Time,End Time,Speaker,Segment Text\n")

            for (item in transcripts) {
                val t = item.transcript
                val formattedDate = AudioTimeUtils.formatDate(t.createdAt)

                val matchingSegments = if (cleanQuery.isBlank()) {
                    item.segments
                } else {
                    val queryTerms = cleanQuery.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                    item.segments.filter { seg ->
                        val lower = "${seg.text} ${seg.speaker} ${t.title} ${t.fileName}".lowercase()
                        queryTerms.all { lower.contains(it) }
                    }
                }

                if (cleanQuery.isNotBlank() && matchingSegments.isEmpty()) {
                    continue
                }

                if (matchingSegments.isEmpty()) {
                    writer.write(
                        listOf(
                            t.id.toString(),
                            escapeCsv(t.title),
                            escapeCsv(t.fileName),
                            t.fileFormat,
                            t.category,
                            formattedDate,
                            "00:00",
                            AudioTimeUtils.formatDuration(t.durationMs),
                            "All",
                            escapeCsv(t.summary.ifBlank { t.fullText })
                        ).joinToString(",") + "\n"
                    )
                } else {
                    for (seg in matchingSegments) {
                        writer.write(
                            listOf(
                                t.id.toString(),
                                escapeCsv(t.title),
                                escapeCsv(t.fileName),
                                t.fileFormat,
                                t.category,
                                formattedDate,
                                AudioTimeUtils.formatDuration(seg.startTimeMs),
                                AudioTimeUtils.formatDuration(seg.endTimeMs),
                                escapeCsv(seg.speaker),
                                escapeCsv(seg.text)
                            ).joinToString(",") + "\n"
                        )
                    }
                }
            }
        }

        return csvFile
    }

    /**
     * Exports transcripts or search results to a beautifully formatted PDF.
     */
    fun exportToPdf(
        context: Context,
        transcripts: List<TranscriptWithSegments>,
        filterQuery: String = ""
    ): File {
        val cleanQuery = filterQuery.trim()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = if (cleanQuery.isBlank()) {
            "AudioScribe_Report_$timestamp.pdf"
        } else {
            "AudioScribe_Search_${cleanQuery.replace("\\W+".toRegex(), "_")}_$timestamp.pdf"
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val pdfFile = File(exportDir, fileName)

        // Filter transcripts to those that actually match the query
        val queryTerms = if (cleanQuery.isBlank()) emptyList() else cleanQuery.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        val activeList = if (queryTerms.isEmpty()) {
            transcripts.map { it to it.segments }
        } else {
            transcripts.mapNotNull { item ->
                val matchingSegments = item.segments.filter { seg ->
                    val lower = "${seg.text} ${seg.speaker} ${item.transcript.title} ${item.transcript.fileName}".lowercase()
                    queryTerms.all { lower.contains(it) }
                }
                if (matchingSegments.isNotEmpty()) {
                    item to matchingSegments
                } else if (queryTerms.all { "${item.transcript.title} ${item.transcript.fileName}".lowercase().contains(it) }) {
                    item to item.segments.take(5)
                } else {
                    null
                }
            }
        }

        val totalMatches = activeList.sumOf { it.second.size }

        try {
            val doc = PdfDocument()

            // Page setup: Standard A4 (595 x 842 points)
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f
            val contentWidth = pageWidth - (margin * 2)

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 17f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 9.5f
                isAntiAlias = true
            }

            val headerBoxPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }

            val transcriptTitlePaint = Paint().apply {
                color = Color.rgb(2, 132, 199)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val badgeTextPaint = Paint().apply {
                color = Color.rgb(14, 116, 144)
                textSize = 8.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
            }

            val speakerPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 9f
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 1f
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas
            var yPos = margin

            fun checkPageBreak(requiredHeight: Float) {
                if (yPos + requiredHeight > pageHeight - margin - 25f) {
                    subtitlePaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("Page $pageNumber", pageWidth - margin, pageHeight - margin + 8f, subtitlePaint)
                    subtitlePaint.textAlign = Paint.Align.LEFT

                    doc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = margin
                }
            }

            // Draw Document Header
            canvas.drawRect(margin, yPos, pageWidth - margin, yPos + 58f, headerBoxPaint)
            canvas.drawText("AudioScribe Transcription Report", margin + 14f, yPos + 24f, titlePaint)
            val filterSubtitle = if (cleanQuery.isBlank()) {
                "Total Audio Transcripts: ${activeList.size} · Generated: ${AudioTimeUtils.formatDate(System.currentTimeMillis())}"
            } else {
                "Search Query: \"$cleanQuery\" · Found $totalMatches matching segments in ${activeList.size} files · Generated: ${AudioTimeUtils.formatDate(System.currentTimeMillis())}"
            }
            canvas.drawText(filterSubtitle, margin + 14f, yPos + 44f, subtitlePaint)
            yPos += 72f

            if (activeList.isEmpty() && cleanQuery.isNotBlank()) {
                canvas.drawText("No matching transcript segments found for query \"$cleanQuery\".", margin + 10f, yPos + 20f, textPaint)
                canvas.drawText("Ensure audio files are fully analyzed or check keyword spelling.", margin + 10f, yPos + 36f, subtitlePaint)
                yPos += 50f
            }

            for ((item, matchingSegments) in activeList) {
                val t = item.transcript
                checkPageBreak(50f)

                canvas.drawLine(margin, yPos, pageWidth - margin, yPos, linePaint)
                yPos += 14f
                canvas.drawText("${t.title} [${t.fileFormat}]", margin, yPos, transcriptTitlePaint)
                yPos += 13f

                val metaText = "File: ${t.fileName} · Category: ${t.category} · Duration: ${AudioTimeUtils.formatDuration(t.durationMs)} · ${matchingSegments.size} segments matching"
                canvas.drawText(metaText, margin, yPos, subtitlePaint)
                yPos += 16f

                for (seg in matchingSegments) {
                    val timeRange = "[${AudioTimeUtils.formatDuration(seg.startTimeMs)} - ${AudioTimeUtils.formatDuration(seg.endTimeMs)}]"
                    val wrappedLines = wrapText(seg.text, textPaint, contentWidth - 140f)
                    val blockHeight = (wrappedLines.size * 12f) + 10f

                    checkPageBreak(blockHeight)

                    canvas.drawText(timeRange, margin, yPos, badgeTextPaint)
                    canvas.drawText(seg.speaker + ":", margin + 74f, yPos, speakerPaint)

                    var lineY = yPos
                    for (line in wrappedLines) {
                        canvas.drawText(line, margin + 138f, lineY, textPaint)
                        lineY += 12f
                    }

                    yPos = lineY + 4f
                }

                yPos += 10f
            }

            subtitlePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Page $pageNumber", pageWidth - margin, pageHeight - margin + 8f, subtitlePaint)
            subtitlePaint.textAlign = Paint.Align.LEFT

            doc.finishPage(page)

            FileOutputStream(pdfFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()
        } catch (_: Exception) {
            // Fallback for headless environments or graphics failures
            FileOutputStream(pdfFile).bufferedWriter().use { writer ->
                writer.write("%PDF-1.4\n")
                writer.write("% AudioScribe Transcription Report\n")
                writer.write("% Query: $cleanQuery\n")
                writer.write("% Total Matches: $totalMatches\n\n")
                for ((item, matchingSegments) in activeList) {
                    writer.write("----------------------------------------\n")
                    writer.write("${item.transcript.title} [${item.transcript.fileFormat}]\n")
                    writer.write("File: ${item.transcript.fileName}\n")
                    for (seg in matchingSegments) {
                        writer.write("[${AudioTimeUtils.formatDuration(seg.startTimeMs)} - ${AudioTimeUtils.formatDuration(seg.endTimeMs)}] ${seg.speaker}: ${seg.text}\n")
                    }
                }
            }
        }

        return pdfFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines.ifEmpty { listOf(text) }
    }

    private fun escapeCsv(value: String): String {
        val sanitized = value.replace("\"", "\"\"")
        return if (sanitized.contains(",") || sanitized.contains("\n") || sanitized.contains("\"")) {
            "\"$sanitized\""
        } else {
            sanitized
        }
    }
}
