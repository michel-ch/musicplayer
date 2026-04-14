package com.musicplayer.app.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.ui.components.ListOptionsDialog

import com.musicplayer.app.ui.components.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onBackClick: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val playlistSongs by viewModel.playlistSongs.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val playbackState by viewModel.playbackController.playbackState.collectAsState()
    val songListState = rememberLazyListState()

    // Auto-scroll to current playing song when viewing playlist songs
    LaunchedEffect(selectedPlaylistId) {
        if (selectedPlaylistId == null) return@LaunchedEffect
        val currentId = playbackState.currentSong?.id ?: return@LaunchedEffect

        val songsList = if (playlistSongs.isEmpty()) {
            snapshotFlow { playlistSongs }.first { it.isNotEmpty() }
        } else playlistSongs

        val songIndex = songsList.indexOfFirst { it.id == currentId }
        if (songIndex < 0) return@LaunchedEffect

        snapshotFlow { songListState.layoutInfo.totalItemsCount }.first { it > 0 }
        songListState.scrollToItem(songIndex)
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showListOptions by remember { mutableStateOf(false) }
    var reversed by remember { mutableStateOf(false) }
    var listPositionEnabled by remember { mutableStateOf(false) }
    var perTrackProgressEnabled by remember { mutableStateOf(false) }

    if (showListOptions) {
        ListOptionsDialog(
            contextTitle = "Playlist",
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
                title = {
                    Text(
                        if (selectedPlaylistId != null) {
                            playlists.find { it.id == selectedPlaylistId }?.name ?: "Playlist"
                        } else "Playlists"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPlaylistId != null) viewModel.clearSelection() else onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedPlaylistId != null) {
                        IconButton(onClick = { showListOptions = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "List Options"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedPlaylistId == null) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create playlist")
                }
            }
        }
    ) { paddingValues ->
        if (selectedPlaylistId == null) {
            // Playlist list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectPlaylist(playlist.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${playlist.songCount} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        } else {
            // Songs in playlist
            LazyColumn(
                state = songListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(playlistSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        isPlaying = song.id == playbackState.currentSong?.id,
                        onClick = { viewModel.playSong(song, playlistSongs) },
                        onMoreClick = {
                            selectedPlaylistId?.let { playlistId ->
                                viewModel.removeSongFromPlaylist(playlistId, song.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
