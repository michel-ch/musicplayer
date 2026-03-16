package com.musicplayer.app.ui.screens.artist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.ui.components.ListOptionsDialog
import com.musicplayer.app.ui.components.SongOptionsSheet

import com.musicplayer.app.ui.components.SongItem
import com.musicplayer.app.ui.screens.playlist.PlaylistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBackClick: () -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
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

    viewModel.loadArtist(artistName)

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
            contextTitle = "Artist",
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
                title = { Text(artistName) },
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
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header with play/shuffle buttons
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        FilledTonalButton(onClick = { viewModel.playAll(songs) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(onClick = { viewModel.playAll(songs.shuffled()) }) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shuffle")
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
