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

// Section indices in the LazyColumn
// 0 = categories header area (all category items)
// After categories: sections in order
private const val SECTION_HEADSET_BLUETOOTH = 0
private const val SECTION_LOOK_AND_FEEL = 1
private const val SECTION_AUDIO = 2
private const val SECTION_ALBUM_ART = 3
private const val SECTION_VISUALIZATION = 4
private const val SECTION_LOCK_SCREEN = 5
private const val SECTION_LIBRARY = 6
private const val SECTION_EXPORT_IMPORT = 7
private const val SECTION_ABOUT = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val darkMode by viewModel.darkMode.collectAsState()
    val scanFolders by viewModel.scanFolders.collectAsState()
    val gaplessPlayback by viewModel.gaplessPlayback.collectAsState()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val showAlbumArt by viewModel.showAlbumArt.collectAsState()
    val highResArt by viewModel.highResArt.collectAsState()
    val showWaveform by viewModel.showWaveform.collectAsState()
    val autoResumeOnHeadset by viewModel.autoResumeOnHeadset.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val showLockScreenControls by viewModel.showLockScreenControls.collectAsState()

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

    // Number of category items at the top
    val categoryCount = 9

    val settingsCategories = listOf(
        SettingsCategory("Headset/Bluetooth", "Auto-resume, controls", Icons.Default.Bluetooth, CategoryIndigo, categoryCount + 0),
        SettingsCategory("Look and Feel", "Theme, colors, animations", Icons.Default.Palette, CategoryPurple, categoryCount + 2),
        SettingsCategory("Audio", "Playback, gapless, crossfade", Icons.Default.MusicNote, CategoryBlue, categoryCount + 4),
        SettingsCategory("Album Art", "Artwork display settings", Icons.Default.Image, CategoryOrange, categoryCount + 6),
        SettingsCategory("Visualization", "Waveform, spectrum display", Icons.Default.Visibility, CategoryPink, categoryCount + 8),
        SettingsCategory("Lock Screen", "Lock screen controls", Icons.Default.Lock, CategoryDeepPurple, categoryCount + 10),
        SettingsCategory("Library", "Scan folders, file management", Icons.Default.LibraryMusic, CategoryTeal, categoryCount + 12),
        SettingsCategory("Export/Import Settings", "Backup and restore", Icons.Default.SaveAlt, CategoryAmber, categoryCount + 14),
        SettingsCategory("About", "Version info", Icons.Default.Info, CategoryCyan, categoryCount + 16),
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

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== Look and Feel =====
            item { SectionHeader("Look and Feel") }

            item {
                SettingsToggleRow(
                    title = "Dark Mode",
                    subtitle = "Use dark theme",
                    checked = darkMode,
                    onToggle = { viewModel.toggleDarkMode() }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== Audio =====
            item { SectionHeader("Audio") }

            item {
                SettingsToggleRow(
                    title = "Gapless Playback",
                    subtitle = "Eliminate silence between tracks",
                    checked = gaplessPlayback,
                    onToggle = { viewModel.toggleGaplessPlayback() }
                )
            }

            item {
                SettingsToggleRow(
                    title = "Crossfade",
                    subtitle = "Fade between tracks",
                    checked = crossfadeEnabled,
                    onToggle = { viewModel.toggleCrossfade() }
                )
            }

            if (crossfadeEnabled) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Crossfade duration: ${crossfadeDuration}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = crossfadeDuration.toFloat(),
                            onValueChange = { viewModel.setCrossfadeDuration(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("10s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }

            // ===== Album Art =====
            item { SectionHeader("Album Art") }

            item {
                SettingsToggleRow(
                    title = "Show Album Art",
                    subtitle = "Display album artwork in lists",
                    checked = showAlbumArt,
                    onToggle = { viewModel.toggleShowAlbumArt() }
                )
            }

            item {
                SettingsToggleRow(
                    title = "High Resolution Art",
                    subtitle = "Load higher quality artwork (uses more memory)",
                    checked = highResArt,
                    onToggle = { viewModel.toggleHighResArt() }
                )
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

            // ===== Lock Screen =====
            item { SectionHeader("Lock Screen") }

            item {
                SettingsToggleRow(
                    title = "Lock Screen Controls",
                    subtitle = "Show playback controls on lock screen",
                    checked = showLockScreenControls,
                    onToggle = { viewModel.toggleShowLockScreenControls() }
                )
            }

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

            // ===== Export/Import =====
            item { SectionHeader("Export/Import") }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.exportSettings() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text("Export Settings", style = MaterialTheme.typography.bodyLarge)
                        Text("Save settings as JSON", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.importSettings() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Text("Import Settings", style = MaterialTheme.typography.bodyLarge)
                        Text("Restore settings from JSON", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(
                        "Version 1.0",
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
