package com.musicplayer.app.ui.screens.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.ui.components.ListOptionsDialog
import com.musicplayer.app.ui.components.SongOptionsSheet

import com.musicplayer.app.ui.components.SongItem
import com.musicplayer.app.ui.screens.playlist.PlaylistViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerDetailScreen(
    composerName: String,
    onBackClick: () -> Unit,
    viewModel: ComposerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    viewModel.loadComposer(composerName)
    val songs by viewModel.composerSongs.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val playbackState by viewModel.playbackController.playbackState.collectAsState()
    val listState = rememberLazyListState()
    var showListOptions by remember { mutableStateOf(false) }

    // Auto-scroll to current playing song when entering this screen
    LaunchedEffect(Unit) {
        val currentId = playbackState.currentSong?.id ?: return@LaunchedEffect

        val songsList = if (songs.isEmpty()) {
            snapshotFlow { songs }.first { it.isNotEmpty() }
        } else songs

        val songIndex = songsList.indexOfFirst { it.id == currentId }
        if (songIndex < 0) return@LaunchedEffect

        // +1 for the header item
        val itemIndex = songIndex + 1

        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        listState.scrollToItem(itemIndex)
    }
    var reversed by remember { mutableStateOf(false) }
    var listPositionEnabled by remember { mutableStateOf(false) }
    var perTrackProgressEnabled by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val playlists by playlistViewModel.playlists.collectAsState()
    val scope = rememberCoroutineScope()

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
            contextTitle = "Composer",
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
                title = { Text(composerName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (songs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${songs.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            IconButton(onClick = { viewModel.playAll(songs) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play all")
                            }
                            IconButton(onClick = { viewModel.playAll(songs.shuffled()) }) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle all")
                            }
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
