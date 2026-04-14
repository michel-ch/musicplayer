package com.musicplayer.app.ui.screens.folder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.ui.components.AlbumArtImage
import com.musicplayer.app.ui.components.AlphabetFastScroller
import com.musicplayer.app.ui.components.ListOptionsDialog
import com.musicplayer.app.ui.components.SongOptionsSheet

import com.musicplayer.app.ui.components.SongItem
import com.musicplayer.app.ui.screens.playlist.PlaylistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserScreen(
    onBackClick: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val folderSongs by viewModel.folderSongs.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val playbackState by viewModel.playbackController.playbackState.collectAsState()
    var showListOptions by remember { mutableStateOf(false) }
    var reversed by remember { mutableStateOf(false) }
    var listPositionEnabled by remember { mutableStateOf(false) }
    var perTrackProgressEnabled by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val playlists by playlistViewModel.playlists.collectAsState()
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()

    // Auto-scroll to current playing song when entering folder song list
    LaunchedEffect(selectedFolder) {
        if (selectedFolder == null) return@LaunchedEffect
        val currentId = playbackState.currentSong?.id ?: return@LaunchedEffect

        val songsList = if (folderSongs.isEmpty()) {
            snapshotFlow { folderSongs }.first { it.isNotEmpty() }
        } else folderSongs

        val songIndex = songsList.indexOfFirst { it.id == currentId }
        if (songIndex < 0) return@LaunchedEffect

        // +1 for the folder header item
        val itemIndex = songIndex + 1

        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        listState.scrollToItem(itemIndex)
    }

    // Android system back button: go back to folder list when viewing songs
    BackHandler(enabled = selectedFolder != null) {
        viewModel.clearSelection()
    }

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
            contextTitle = folders.find { it.path == selectedFolder }?.name ?: "Folder",
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
                        if (selectedFolder != null) {
                            folders.find { it.path == selectedFolder }?.name ?: "Folder"
                        } else "Folders"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFolder != null) viewModel.clearSelection() else onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (selectedFolder != null) {
                        IconButton(onClick = { showListOptions = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "List Options"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = selectedFolder,
            transitionSpec = {
                if (targetState != null) {
                    // Entering song list: slide in from right
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    // Going back to folder grid: slide in from left
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "folder_content"
        ) { folder ->
        if (folder == null) {
            // Folder grid with thumbnails
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(folders, key = { it.path }) { folder ->
                    Card(
                        modifier = Modifier.clickable { viewModel.selectFolder(folder.path) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            AlbumArtImage(
                                uri = folder.albumArtUri,
                                size = 140.dp,
                                cornerRadius = 12.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folder.songCount} songs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // Songs in selected folder with rich header
            val currentFolder = folders.find { it.path == selectedFolder }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Rich folder header
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            // Background art
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (currentFolder?.albumArtUri != null) {
                                    AlbumArtImage(
                                        uri = currentFolder.albumArtUri,
                                        size = 200.dp,
                                        cornerRadius = 0.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            // Gradient overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                            )
                                        )
                                    )
                            )
                            // Content overlay
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    text = currentFolder?.name ?: "",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Music",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${folderSongs.size} songs  |  ${viewModel.formatTotalDuration(folderSongs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                // Action buttons row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.shuffleAndPlay(folderSongs) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            Icons.Default.Shuffle,
                                            contentDescription = "Shuffle",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.playAll(folderSongs) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { /* Search within folder */ },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { /* Select mode */ },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            Icons.Default.Checklist,
                                            contentDescription = "Select",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(folderSongs, key = { it.id }) { song ->
                        SongItem(
                            song = song,
                            isPlaying = song.id == playbackState.currentSong?.id,
                            onClick = { viewModel.playSong(song, folderSongs) },
                            onLongClick = { selectedSong = song }
                        )
                    }
                }

                // Fast scroller on right edge
                if (folderSongs.size > 20) {
                    AlphabetFastScroller(
                        listState = listState,
                        items = folderSongs.map { it.title },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                    )
                }
            }
        }
        }
    }
}
