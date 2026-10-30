package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.example.ui.theme.HighlightBg
import com.example.ui.theme.HighlightText

@Composable
fun HighlightedText(
    text: String,
    highlightQuery: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    highlightBgColor: Color = HighlightBg,
    highlightTextColor: Color = HighlightText
) {
    if (highlightQuery.isBlank()) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
        return
    }

    val annotatedString = buildAnnotatedString {
        val lowerText = text.lowercase()
        val queryTerms = highlightQuery.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        // Find all match ranges
        val ranges = mutableListOf<Pair<Int, Int>>()
        for (term in queryTerms) {
            var start = 0
            while (start < text.length) {
                val index = lowerText.indexOf(term, start)
                if (index == -1) break
                ranges.add(Pair(index, index + term.length))
                start = index + term.length
            }
        }

        // Merge overlapping ranges
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<Pair<Int, Int>>()
        for (range in sorted) {
            if (merged.isEmpty()) {
                merged.add(range)
            } else {
                val last = merged.last()
                if (range.first <= last.second) {
                    merged[merged.size - 1] = Pair(last.first, maxOf(last.second, range.second))
                } else {
                    merged.add(range)
                }
            }
        }

        var currentIndex = 0
        for (match in merged) {
            if (match.first > currentIndex) {
                append(text.substring(currentIndex, match.first))
            }
            val matchText = text.substring(match.first, match.second)
            pushStyle(
                SpanStyle(
                    background = highlightBgColor,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            )
            append(matchText)
            pop()
            currentIndex = match.second
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight
    )
}
