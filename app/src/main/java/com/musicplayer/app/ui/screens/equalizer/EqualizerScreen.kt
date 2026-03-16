package com.musicplayer.app.ui.screens.equalizer

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicplayer.app.ui.components.EqBandSlider
import com.musicplayer.app.ui.components.RotaryKnob
import com.musicplayer.app.ui.theme.EqGreenIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val isEnabled by viewModel.isEnabled.collectAsState()
    val bandLevels by viewModel.bandLevels.collectAsState()
    val bandFrequencies by viewModel.bandFrequencies.collectAsState()
    val presetNames by viewModel.presetNames.collectAsState()
    val currentPreset by viewModel.currentPreset.collectAsState()
    val minLevel by viewModel.minBandLevel.collectAsState()
    val maxLevel by viewModel.maxBandLevel.collectAsState()
    val preampLevel by viewModel.preampLevel.collectAsState()
    val toneBass by viewModel.toneBass.collectAsState()
    val toneTreble by viewModel.toneTreble.collectAsState()
    val limiterStrength by viewModel.limiterStrength.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = if (isEnabled) EqGreenIndicator else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.setEnabled(it) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reset") },
                                onClick = {
                                    bandLevels.indices.forEach { viewModel.setBandLevel(it, 0) }
                                    viewModel.setPreampLevel(0f)
                                    viewModel.setToneBass(50f)
                                    viewModel.setToneTreble(50f)
                                    viewModel.setLimiterStrength(0f)
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Status bar
            Text(
                text = buildString {
                    append("NO DVC ")
                    if (isEnabled) append("EQ ") else append("-- ")
                    append("${bandLevels.size} ")
                    if (toneBass != 50f || toneTreble != 50f) append("TON ") else append("--- ")
                    if (limiterStrength > 0f) append("LMT") else append("---")
                },
                style = MaterialTheme.typography.labelSmall,
                color = EqGreenIndicator.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Tabs
            TabRow(
                selectedTabIndex = activeTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { viewModel.setActiveTab(0) },
                    text = { Text("Equ") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { viewModel.setActiveTab(1) },
                    text = { Text("Tone") }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { viewModel.setActiveTab(2) },
                    text = { Text("Limit") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (activeTab) {
                    0 -> EquTab(
                        isEnabled = isEnabled,
                        bandLevels = bandLevels,
                        bandFrequencies = bandFrequencies,
                        presetNames = presetNames,
                        currentPreset = currentPreset,
                        minLevel = minLevel,
                        maxLevel = maxLevel,
                        preampLevel = preampLevel,
                        onBandLevelChange = viewModel::setBandLevel,
                        onPresetSelect = viewModel::usePreset,
                        onPreampChange = viewModel::setPreampLevel
                    )
                    1 -> ToneTab(
                        isEnabled = isEnabled,
                        toneBass = toneBass,
                        toneTreble = toneTreble,
                        onBassChange = viewModel::setToneBass,
                        onTrebleChange = viewModel::setToneTreble
                    )
                    2 -> LimitTab(
                        isEnabled = isEnabled,
                        limiterStrength = limiterStrength,
                        onLimiterChange = viewModel::setLimiterStrength
                    )
                }
            }
        }
    }
}

@Composable
private fun EquTab(
    isEnabled: Boolean,
    bandLevels: List<Int>,
    bandFrequencies: List<Int>,
    presetNames: List<String>,
    currentPreset: Int,
    minLevel: Int,
    maxLevel: Int,
    preampLevel: Float,
    onBandLevelChange: (Int, Int) -> Unit,
    onPresetSelect: (Int) -> Unit,
    onPreampChange: (Float) -> Unit
) {
    // Preset selector
    var presetExpanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { presetExpanded = true },
            enabled = isEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                if (currentPreset >= 0 && currentPreset < presetNames.size)
                    presetNames[currentPreset]
                else "Custom",
                style = MaterialTheme.typography.labelLarge
            )
        }
        DropdownMenu(
            expanded = presetExpanded,
            onDismissRequest = { presetExpanded = false }
        ) {
            presetNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onPresetSelect(index)
                        presetExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (bandLevels.isNotEmpty() && bandFrequencies.isNotEmpty()) {
        // Preamp slider
        Text(
            text = "Preamp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = preampLevel,
                onValueChange = onPreampChange,
                valueRange = -15f..15f,
                enabled = isEnabled,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "%+.1fdB".format(preampLevel),
                style = MaterialTheme.typography.labelSmall,
                color = if (preampLevel != 0f) EqGreenIndicator else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Band sliders
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bandLevels.forEachIndexed { index, level ->
                EqBandSlider(
                    level = level,
                    minLevel = minLevel,
                    maxLevel = maxLevel,
                    frequency = formatFreq(bandFrequencies.getOrNull(index) ?: 0),
                    enabled = isEnabled,
                    onLevelChange = { onBandLevelChange(index, it) }
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Equalizer not available.\nStart playing a song first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToneTab(
    isEnabled: Boolean,
    toneBass: Float,
    toneTreble: Float,
    onBassChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RotaryKnob(
            value = toneBass,
            label = "Bass",
            enabled = isEnabled,
            onValueChange = onBassChange
        )
        RotaryKnob(
            value = toneTreble,
            label = "Treble",
            enabled = isEnabled,
            onValueChange = onTrebleChange
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = "Adjust bass and treble independently.\n50% = neutral, 0% = cut, 100% = boost.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun LimitTab(
    isEnabled: Boolean,
    limiterStrength: Float,
    onLimiterChange: (Float) -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Limiter",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Prevents audio clipping when EQ boost is applied.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    Slider(
        value = limiterStrength,
        onValueChange = onLimiterChange,
        valueRange = 0f..100f,
        enabled = isEnabled,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Off",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${limiterStrength.toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = if (limiterStrength > 0f) EqGreenIndicator else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Max",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatFreq(freqHz: Int): String {
    return if (freqHz >= 1000) "${freqHz / 1000}k" else "${freqHz}"
}
