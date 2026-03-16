package com.musicplayer.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.ui.theme.CategoryBlue
import com.musicplayer.app.ui.theme.CategoryPurple
import com.musicplayer.app.ui.theme.CategoryPink
import com.musicplayer.app.ui.theme.CategoryGreen
import com.musicplayer.app.ui.theme.CategoryTeal
import com.musicplayer.app.ui.theme.CategoryOrange
import com.musicplayer.app.ui.theme.CategoryIndigo
import com.musicplayer.app.ui.theme.CategoryBrown
import com.musicplayer.app.ui.theme.CategoryDeepPurple
import com.musicplayer.app.ui.theme.CategoryCyan
import com.musicplayer.app.ui.theme.CategoryAmber

data class LibraryCategoryItem(
    val label: String,
    val icon: ImageVector,
    val subtitle: String,
    val circleColor: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAllSongsClick: () -> Unit,
    onFoldersClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onGenresClick: () -> Unit,
    onYearsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFoldersHierarchyClick: () -> Unit = {},
    onAlbumArtistsClick: () -> Unit = {},
    onComposersClick: () -> Unit = {},
    onStreamsClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val categories = listOf(
        LibraryCategoryItem("All Songs", Icons.Default.MusicNote, "${songs.size} tracks", CategoryBlue, onAllSongsClick),
        LibraryCategoryItem("Folders", Icons.Default.Folder, "Browse by folder", CategoryOrange, onFoldersClick),
        LibraryCategoryItem("Folders Hierarchy", Icons.Default.AccountTree, "Nested folder view", CategoryBrown, onFoldersHierarchyClick),
        LibraryCategoryItem("Albums", Icons.Default.Album, "${albums.size} albums", CategoryPurple, onAlbumsClick),
        LibraryCategoryItem("Artists", Icons.Default.Person, "${artists.size} artists", CategoryPink, onArtistsClick),
        LibraryCategoryItem("Album Artists", Icons.Default.LibraryMusic, "Browse album artists", CategoryDeepPurple, onAlbumArtistsClick),
        LibraryCategoryItem("Composers", Icons.Default.Piano, "Browse composers", CategoryCyan, onComposersClick),
        LibraryCategoryItem("Genres", Icons.Default.Category, "Browse by genre", CategoryGreen, onGenresClick),
        LibraryCategoryItem("Years", Icons.Default.CalendarMonth, "Browse by year", CategoryTeal, onYearsClick),
        LibraryCategoryItem("Playlists", Icons.AutoMirrored.Filled.QueueMusic, "Your playlists", CategoryIndigo, onPlaylistsClick),
        LibraryCategoryItem("Streams", Icons.Default.Radio, "Internet streams", CategoryAmber, onStreamsClick),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(categories) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = category.onClick)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(category.circleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = category.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
