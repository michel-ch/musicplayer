package com.musicplayer.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicplayer.app.domain.model.Playlist
import com.musicplayer.app.domain.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionOptionsSheet(
    title: String,
    subtitle: String,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onAddAllToPlaylist: (List<Song>, Long) -> Unit,
    onDeleteAll: ((List<Song>) -> Unit)? = null,
    playlists: List<Playlist>,
    onCreatePlaylist: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    if (showDeleteDialog && onDeleteAll != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Songs") },
            text = { Text("Are you sure you want to delete all ${songs.size} songs in \"$title\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll(songs)
                    showDeleteDialog = false
                    onDismiss()
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists,
            onPlaylistSelected = { playlistId ->
                onAddAllToPlaylist(songs, playlistId)
                showPlaylistPicker = false
                onDismiss()
            },
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showPlaylistPicker = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$subtitle - ${songs.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            CollectionOptionRow(
                icon = Icons.Default.PlayArrow,
                label = "Play All",
                onClick = {
                    onPlayAll(songs)
                    onDismiss()
                }
            )

            CollectionOptionRow(
                icon = Icons.Default.Shuffle,
                label = "Shuffle All",
                onClick = {
                    onShuffleAll(songs)
                    onDismiss()
                }
            )

            CollectionOptionRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Add All to Playlist",
                onClick = { showPlaylistPicker = true }
            )

            if (onDeleteAll != null) {
                CollectionOptionRow(
                    icon = Icons.Default.Delete,
                    label = "Delete All",
                    onClick = { showDeleteDialog = true }
                )
            }
        }
    }
}

@Composable
private fun CollectionOptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
