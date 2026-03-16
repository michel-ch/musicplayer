package com.musicplayer.app.ui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.ui.components.AlbumArtImage
import com.musicplayer.app.ui.components.ListOptionsDialog
import com.musicplayer.app.ui.components.SongOptionsSheet

import com.musicplayer.app.ui.components.SongItem
import com.musicplayer.app.ui.screens.playlist.PlaylistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBackClick: () -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val playbackState by viewModel.playbackController.playbackState.collectAsState()
    var showListOptions by remember { mutableStateOf(false) }
    var reversed by remember { mutableStateOf(false) }
    var listPositionEnabled by remember { mutableStateOf(false) }
    var perTrackProgressEnabled by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val playlists by playlistViewModel.playlists.collectAsState()
    val scope = rememberCoroutineScope()

    viewModel.loadAlbum(albumId)

    if (selectedSong != null) {
        SongOptionsSheet(
            song = selectedSong!!,
            onDismiss = { selectedSong = null },
            onPlayNext = { song -> viewModel.playbackController.addToQueue(song) },
            onAddToPlaylist = { song, playlistId ->
                scope.launch { playlistViewModel.addSongToPlaylist(playlistId, song.id) }
            },
            onDelete = { song -> viewModel.deleteSong(song) },
            playlists = playlists,
            onCreatePlaylist = { name ->
                scope.launch { playlistViewModel.createPlaylist(name) }
            }
        )
    }

    if (showListOptions) {
        ListOptionsDialog(
            contextTitle = "Album",
            currentSort = sortOption,
            reversed = reversed,
            listPositionEnabled = listPositionEnabled,
            perTrackProgressEnabled = perTrackProgressEnabled,
            onSortSelected = { viewModel.setSortOption(it) },
            onReverseToggle = { reversed = it },
            onListPositionToggle = { listPositionEnabled = it },
            onPerTrackProgressToggle = { perTrackProgressEnabled = it },
            onDismiss = { showListOptions = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Album") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showListOptions = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "List Options"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Album header with blurred background
            if (songs.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        // Blurred background
                        AsyncImage(
                            model = songs.first().albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(30.dp),
                            contentScale = ContentScale.Crop
                        )
                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                        // Foreground content
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AlbumArtImage(
                                uri = songs.first().albumArtUri,
                                size = 180.dp,
                                cornerRadius = 12.dp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = songs.first().album,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = songs.first().artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${songs.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action buttons row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalButton(onClick = { viewModel.playAll(songs.shuffled()) }) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                        FilledTonalButton(onClick = { viewModel.playAll(songs) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play")
                        }
                    }
                }
            }

            items(songs, key = { it.id }) { song ->
                SongItem(
                    song = song,
                    isPlaying = song.id == playbackState.currentSong?.id,
                    onClick = { viewModel.playSong(song, songs) },
                    onLongClick = { selectedSong = song }
                )
            }
        }
    }
}
