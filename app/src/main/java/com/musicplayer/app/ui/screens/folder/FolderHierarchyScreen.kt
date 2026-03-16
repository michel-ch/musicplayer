package com.musicplayer.app.ui.screens.folder

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderHierarchyScreen(
    onBackClick: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsState()
    val pathStack = remember { mutableStateListOf<String>() }

    val currentFolders = if (pathStack.isEmpty()) {
        // Show root-level parent folders
        folders.map { java.io.File(it.path).parent ?: "/" }.distinct().sorted()
    } else {
        val currentPath = pathStack.last()
        folders.filter { it.path.startsWith(currentPath) }
            .map { it.path }
            .filter { path ->
                val relative = path.removePrefix(currentPath).trimStart('/', '\\')
                !relative.contains('/') && !relative.contains('\\')
            }
            .distinct()
            .sorted()
    }

    val currentTitle = if (pathStack.isEmpty()) "Folders Hierarchy" else java.io.File(pathStack.last()).name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (pathStack.isNotEmpty()) pathStack.removeLast() else onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            if (pathStack.isEmpty()) {
                items(currentFolders) { parentPath ->
                    val childCount = folders.count { it.path.startsWith(parentPath) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pathStack.add(parentPath) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = java.io.File(parentPath).name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "$childCount folders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                val matchingFolders = folders.filter { it.path.startsWith(pathStack.last()) }
                items(matchingFolders, key = { it.path }) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectFolder(folder.path) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = folder.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "${folder.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
