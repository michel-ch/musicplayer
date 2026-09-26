package com.musicplayer.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch
import java.io.File

data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val circleColor: Color,
    val sectionIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val scanFolders by viewModel.scanFolders.collectAsState()
    val showWaveform by viewModel.showWaveform.collectAsState()
    val autoResumeOnHeadset by viewModel.autoResumeOnHeadset.collectAsState()
    val resumeOnAppForeground by viewModel.resumeOnAppForeground.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val continueToNextFolder by viewModel.continueToNextFolder.collectAsState()
    val skipForwardSeconds by viewModel.skipForwardSeconds.collectAsState()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Back behavior: scroll to top first, then navigate back
    val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    val handleBack = {
        if (!isAtTop) {
            coroutineScope.launch { listState.animateScrollToItem(0) }
        } else {
            onBackClick()
        }
    }

    BackHandler { handleBack() }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val path = extractPathFromTreeUri(it)
            if (path != null) {
                viewModel.addScanFolder(path, it.toString())
            }
        }
    }

    // LazyColumn index of each SectionHeader. These must mirror the item() calls
    // below exactly (dividers and the variable scan-folder list included).
    val categoryCount = 6
    val idxHeadset = categoryCount + 1                       // divider, header
    val idxAudio = idxHeadset + 1 + 2 + 1                    // header, 2 rows, divider
    val idxVisualization = idxAudio + 1 + 2 + 1                // header, 2 rows, divider
    val idxScreen = idxVisualization + 1 + 1 + 1
    val idxLibrary = idxScreen + 1 + 1 + 1
    // header, folders label, N folders, add button, spacer, rescan row, divider
    val idxAbout = idxLibrary + 1 + 1 + scanFolders.size + 1 + 1 + 1 + 1

    val settingsCategories = listOf(
        SettingsCategory("Headset/Bluetooth", "Auto-resume, controls", Icons.Default.Bluetooth, CategoryIndigo, idxHeadset),
        SettingsCategory("Audio", "Skip forward, folder continuation", Icons.Default.MusicNote, CategoryBlue, idxAudio),
        SettingsCategory("Visualization", "Waveform display", Icons.Default.Visibility, CategoryPink, idxVisualization),
        SettingsCategory("Screen", "Keep screen on", Icons.Default.Lock, CategoryDeepPurple, idxScreen),
        SettingsCategory("Library", "Scan folders, file management", Icons.Default.LibraryMusic, CategoryTeal, idxLibrary),
        SettingsCategory("About", "Version info", Icons.Default.Info, CategoryCyan, idxAbout),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
            // Category list items (clickable to scroll to section)
            items(settingsCategories.size) { index ->
                val category = settingsCategories[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(category.sectionIndex)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(category.circleColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.title,
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

            // Divider after categories
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

            // ===== Headset/Bluetooth =====
            item { SectionHeader("Headset/Bluetooth") }

            item {
                SettingsToggleRow(
                    title = "Auto Resume on Headset",
                    subtitle = "Resume playback when headset is connected",
                    checked = autoResumeOnHeadset,
                    onToggle = { viewModel.toggleAutoResumeOnHeadset() }
                )
            }

            item {
                SettingsToggleRow(
                    title = "Resume on App Open",
                    subtitle = "Resume paused playback when opening the app while Bluetooth is connected",
                    checked = resumeOnAppForeground,
                    onToggle = { viewModel.toggleResumeOnAppForeground() }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== Audio =====
            item { SectionHeader("Audio") }

            item {
                SettingsToggleRow(
                    title = "Continue to Next Folder",
                    subtitle = "When queue ends, play next folder alphabetically",
                    checked = continueToNextFolder,
                    onToggle = { viewModel.toggleContinueToNextFolder() }
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Skip Forward", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Jump forward by this amount on Now Playing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsViewModel.SKIP_FORWARD_OPTIONS.forEach { seconds ->
                            FilterChip(
                                selected = skipForwardSeconds == seconds,
                                onClick = { viewModel.setSkipForwardSeconds(seconds) },
                                label = { Text(if (seconds >= 60) "${seconds / 60} min" else "${seconds}s") }
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== Visualization =====
            item { SectionHeader("Visualization") }

            item {
                SettingsToggleRow(
                    title = "Show Waveform",
                    subtitle = "Animated waveform on Now Playing screen",
                    checked = showWaveform,
                    onToggle = { viewModel.toggleShowWaveform() }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== Screen =====
            item { SectionHeader("Screen") }

            item {
                SettingsToggleRow(
                    title = "Keep Screen On",
                    subtitle = "Prevent screen from turning off during playback",
                    checked = keepScreenOn,
                    onToggle = { viewModel.toggleKeepScreenOn() }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== Library =====
            item { SectionHeader("Library") }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Music Folders", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Folders to scan for music files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(scanFolders.sorted().size) { index ->
                val folder = scanFolders.sorted()[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = File(folder).name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = folder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.removeScanFolder(folder) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove folder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                TextButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Add Folder")
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.rescanLibrary() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Rescan Library", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Scan device for new music files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== About =====
            item { SectionHeader("About") }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text("Music Player", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    val versionName = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
                        } catch (_: Exception) { "?" }
                    }
                    Text(
                        "Version $versionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() }
        )
    }
}

private fun extractPathFromTreeUri(uri: Uri): String? {
    try {
        val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null

        val colonIndex = docId.indexOf(':')

        // No colon — treat docId as a direct path if it looks like one
        if (colonIndex < 0) {
            return if (docId.startsWith("/")) docId else null
        }

        val type = docId.substring(0, colonIndex)
        val relativePath = docId.substring(colonIndex + 1)

        // "raw:" format — relativePath is already an absolute filesystem path
        if (type.equals("raw", ignoreCase = true)) {
            return relativePath
        }

        // "home:" format — relative to user home (Documents) on some devices
        if (type.equals("home", ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory().absolutePath
            return if (relativePath.isEmpty()) "$base/Documents"
            else "$base/Documents/$relativePath"
        }

        // "primary:" — internal shared storage
        if (type.equals("primary", ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory().absolutePath
            return if (relativePath.isEmpty()) base else "$base/$relativePath"
        }

        // Anything else (e.g. "1234-ABCD:Music") — SD card / secondary storage
        return if (relativePath.isEmpty()) "/storage/$type" else "/storage/$type/$relativePath"
    } catch (_: Exception) {
        return null
    }
}
