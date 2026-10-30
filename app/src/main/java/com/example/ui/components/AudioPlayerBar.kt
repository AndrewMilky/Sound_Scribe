package com.example.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AudioTimeUtils
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.sin

@Composable
fun AudioPlayerBar(
    audioUriStr: String,
    totalDurationMs: Long,
    requestedSeekMs: Long?,
    onSeekHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    val maxDuration = if (totalDurationMs > 0) totalDurationMs else 120000L

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var hasRealAudio by remember { mutableStateOf(false) }

    // Initialize or release MediaPlayer with robust error handling
    DisposableEffect(audioUriStr) {
        if (audioUriStr.isNotBlank()) {
            try {
                val uri = Uri.parse(audioUriStr)
                val player = MediaPlayer()

                // Set error listener before any other operations to suppress default error logging
                player.setOnErrorListener { _, _, _ ->
                    hasRealAudio = false
                    true // Return true to consume the error event and prevent bubbling
                }

                player.setOnPreparedListener {
                    hasRealAudio = true
                }

                player.setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0L
                }

                var canPlay = false
                if (uri.scheme == "file") {
                    val filePath = uri.path
                    if (!filePath.isNullOrBlank()) {
                        val file = File(filePath)
                        if (file.exists() && file.length() > 44) {
                            player.setDataSource(file.absolutePath)
                            canPlay = true
                        }
                    }
                } else if (uri.scheme == "content") {
                    player.setDataSource(context, uri)
                    canPlay = true
                }

                if (canPlay) {
                    player.prepareAsync()
                    mediaPlayer = player
                } else {
                    hasRealAudio = false
                    player.release()
                }
            } catch (_: Exception) {
                hasRealAudio = false
                try { mediaPlayer?.release() } catch (_: Exception) { }
                mediaPlayer = null
            }
        }

        onDispose {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
            } catch (_: Exception) { }
            try {
                mediaPlayer?.release()
            } catch (_: Exception) { }
            mediaPlayer = null
        }
    }

    // Handle requested external seek (e.g. user clicked a timestamp in the transcript)
    LaunchedEffect(requestedSeekMs) {
        requestedSeekMs?.let { seekMs ->
            currentPositionMs = seekMs.coerceIn(0L, maxDuration)
            if (hasRealAudio && mediaPlayer != null) {
                try {
                    mediaPlayer?.seekTo(currentPositionMs.toInt())
                } catch (_: Exception) { }
            }
            isPlaying = true
            onSeekHandled()
        }
    }

    // Playback ticker coroutine
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(100)
            if (hasRealAudio && mediaPlayer != null) {
                try {
                    currentPositionMs = mediaPlayer!!.currentPosition.toLong()
                } catch (_: Exception) {
                    currentPositionMs += 100
                }
            } else {
                currentPositionMs += 100
            }
            if (currentPositionMs >= maxDuration) {
                currentPositionMs = 0L
                isPlaying = false
            }
        }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            if (hasRealAudio) {
                try { mediaPlayer?.pause() } catch (_: Exception) { }
            }
            isPlaying = false
        } else {
            if (hasRealAudio) {
                try {
                    mediaPlayer?.seekTo(currentPositionMs.toInt())
                    mediaPlayer?.start()
                } catch (_: Exception) {
                    hasRealAudio = false
                }
            }
            isPlaying = true
        }
    }

    fun seekRelative(deltaMs: Long) {
        val target = (currentPositionMs + deltaMs).coerceIn(0L, maxDuration)
        currentPositionMs = target
        if (hasRealAudio) {
            try { mediaPlayer?.seekTo(target.toInt()) } catch (_: Exception) { }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_player_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Waveform Canvas
            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            val progressFraction = (currentPositionMs.toFloat() / maxDuration).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val barCount = 42
                    val barWidth = size.width / (barCount * 1.6f)
                    val gap = barWidth * 0.6f

                    for (i in 0 until barCount) {
                        val barFraction = i.toFloat() / barCount
                        val heightMultiplier = 0.25f + 0.65f * kotlin.math.abs(sin(i * 0.45).toFloat())
                        val barHeight = size.height * heightMultiplier
                        val x = i * (barWidth + gap)
                        val y = (size.height - barHeight) / 2f

                        val color = if (barFraction <= progressFraction) primaryColor else outlineColor
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Time Slider
            Slider(
                value = currentPositionMs.toFloat(),
                onValueChange = { newPos ->
                    currentPositionMs = newPos.toLong()
                    if (hasRealAudio) {
                        try { mediaPlayer?.seekTo(newPos.toInt()) } catch (_: Exception) { }
                    }
                },
                valueRange = 0f..maxDuration.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_scrubber_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            )

            // Timestamps & Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AudioTimeUtils.formatDuration(currentPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Central playback buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { seekRelative(-5000L) },
                        modifier = Modifier.size(36.dp).testTag("rewind_5s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 5s",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        onClick = { togglePlayPause() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp).testTag("play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { seekRelative(5000L) },
                        modifier = Modifier.size(36.dp).testTag("forward_5s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 5s",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = AudioTimeUtils.formatDuration(maxDuration),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
