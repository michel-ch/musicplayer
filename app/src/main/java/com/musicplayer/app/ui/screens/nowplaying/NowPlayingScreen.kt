package com.musicplayer.app.ui.screens.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.player.RepeatMode
import com.musicplayer.app.ui.components.AlbumArtImage
import com.musicplayer.app.ui.components.SongArtModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

private enum class SwipeDirection { VERTICAL, HORIZONTAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBackClick: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val song = playbackState.currentSong

    // Adjacent songs from queue for thumbnails
    val queue by viewModel.queueManager.queue.collectAsState()
    val currentQueueIndex by viewModel.queueManager.currentIndex.collectAsState()
    val prevSong = queue.getOrNull(currentQueueIndex - 1)
    val nextSong = queue.getOrNull(currentQueueIndex + 1)

    // Swipe state
    val offsetY = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    var swipeDirection by remember { mutableStateOf<SwipeDirection?>(null) }
    val swipeCoroutineScope = rememberCoroutineScope()
    val dismissThreshold = 300f
    val changeTrackThreshold = 120f

    // Background alpha fades as user swipes down — reveals previous screen
    val backgroundAlpha = (1f - (offsetY.value / 600f)).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        // Fading scrim — reveals what's behind during swipe down
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = backgroundAlpha))
        )

        // Content that moves with swipe
        Scaffold(
            modifier = Modifier.offset { IntOffset(0, offsetY.value.roundToInt()) },
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (song == null) {
                    Text(
                        text = "No song playing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    return@Scaffold
                }

                // Swipeable zone: prev thumbnail + artwork + next thumbnail + waveform + song info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            val width = size.width.toFloat()

                            detectDragGestures(
                                onDragStart = { swipeDirection = null },
                                onDragEnd = {
                                    swipeCoroutineScope.launch {
                                        when (swipeDirection) {
                                            SwipeDirection.VERTICAL -> {
                                                if (offsetY.value > dismissThreshold) {
                                                    onDismiss()
                                                } else {
                                                    offsetY.animateTo(0f, animationSpec = spring())
                                                }
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                when {
                                                    offsetX.value < -changeTrackThreshold -> {
                                                        // Swipe left → next song
                                                        offsetX.animateTo(-width, tween(180))
                                                        viewModel.skipToNext()
                                                        offsetX.snapTo(width)
                                                        offsetX.animateTo(0f, tween(200))
                                                    }
                                                    offsetX.value > changeTrackThreshold -> {
                                                        // Swipe right → previous song
                                                        offsetX.animateTo(width, tween(180))
                                                        viewModel.skipToPreviousForced()
                                                        offsetX.snapTo(-width)
                                                        offsetX.animateTo(0f, tween(200))
                                                    }
                                                    else -> {
                                                        offsetX.animateTo(0f, spring())
                                                    }
                                                }
                                            }
                                            null -> {
                                                offsetY.animateTo(0f, spring())
                                                offsetX.animateTo(0f, spring())
                                            }
                                        }
                                        swipeDirection = null
                                    }
                                },
                                onDragCancel = {
                                    swipeCoroutineScope.launch {
                                        offsetY.animateTo(0f, spring())
                                        offsetX.animateTo(0f, spring())
                                        swipeDirection = null
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (swipeDirection == null) {
                                        swipeDirection = if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                            SwipeDirection.HORIZONTAL
                                        } else {
                                            SwipeDirection.VERTICAL
                                        }
                                    }
                                    swipeCoroutineScope.launch {
                                        when (swipeDirection) {
                                            SwipeDirection.VERTICAL -> {
                                                val newValue =
                                                    (offsetY.value + dragAmount.y).coerceAtLeast(0f)
                                                offsetY.snapTo(newValue)
                                            }
                                            SwipeDirection.HORIZONTAL -> {
                                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                            }
                                            null -> {}
                                        }
                                    }
                                }
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Three-panel artwork row: [prev thumbnail | current art | next thumbnail]
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val thumbSize = 68.dp
                        val spacing = 10.dp
                        // Current art fills remaining width after the two thumbnails and spacing
                        val artSize = (maxWidth - (thumbSize + spacing) * 2)
                            .coerceAtLeast(120.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing)
                        ) {
                            // Previous song thumbnail (left)
                            if (prevSong != null) {
                                AlbumArtImage(
                                    uri = SongArtModel(
                                        prevSong.uri,
                                        prevSong.albumArtUri,
                                        prevSong.filePath
                                    ),
                                    size = thumbSize,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.alpha(0.55f)
                                )
                            } else {
                                // Empty spacer keeps layout stable when no prev song
                                Spacer(modifier = Modifier.size(thumbSize))
                            }

                            // Current song — large center artwork
                            AlbumArtImage(
                                uri = SongArtModel(song.uri, song.albumArtUri, song.filePath),
                                size = artSize,
                                cornerRadius = 16.dp
                            )

                            // Next song thumbnail (right)
                            if (nextSong != null) {
                                AlbumArtImage(
                                    uri = SongArtModel(
                                        nextSong.uri,
                                        nextSong.albumArtUri,
                                        nextSong.filePath
                                    ),
                                    size = thumbSize,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.alpha(0.55f)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(thumbSize))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Animated Waveform Bars
                    AnimatedWaveformBars(
                        isPlaying = playbackState.isPlaying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Song Info
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Seek Bar (outside swipe zone)
                var isSeeking by remember { mutableStateOf(false) }
                var seekPosition by remember { mutableFloatStateOf(0f) }

                Slider(
                    value = if (isSeeking) seekPosition else playbackState.progress,
                    onValueChange = {
                        isSeeking = true
                        seekPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(seekPosition)
                        isSeeking = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(playbackState.currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDuration(playbackState.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Previous
                    FilledTonalIconButton(
                        onClick = { viewModel.skipToPrevious() },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play/Pause
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause
                            else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Next
                    FilledTonalIconButton(
                        onClick = { viewModel.skipToNext() },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Repeat
                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            imageVector = when (playbackState.repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (playbackState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedWaveformBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 40
) {
    val maxHeight = 48.dp

    val barSpecs = remember {
        List(barCount) {
            BarSpec(
                minHeight = 0.1f + Random.nextFloat() * 0.1f,
                maxHeight = 0.4f + Random.nextFloat() * 0.6f,
                duration = 400 + Random.nextInt(600),
                alpha = 0.5f + Random.nextFloat() * 0.5f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        barSpecs.forEach { spec ->
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = spec.minHeight,
                targetValue = if (isPlaying) spec.maxHeight else spec.minHeight,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = spec.duration,
                        easing = LinearEasing
                    ),
                    repeatMode = AnimRepeatMode.Reverse
                ),
                label = "bar"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 0.5.dp)
                    .height(maxHeight * heightFraction)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = spec.alpha)
                    )
            )
        }
    }
}

private data class BarSpec(
    val minHeight: Float,
    val maxHeight: Float,
    val duration: Int,
    val alpha: Float
)

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
