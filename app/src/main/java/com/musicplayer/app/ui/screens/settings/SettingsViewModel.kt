package com.musicplayer.app.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val musicRepository: MusicRepository
) : ViewModel() {

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val GAPLESS_PLAYBACK_KEY = booleanPreferencesKey("gapless_playback")
        private val CROSSFADE_ENABLED_KEY = booleanPreferencesKey("crossfade_enabled")
        private val CROSSFADE_DURATION_KEY = intPreferencesKey("crossfade_duration")
        private val SHOW_ALBUM_ART_KEY = booleanPreferencesKey("show_album_art")
        private val HIGH_RES_ART_KEY = booleanPreferencesKey("high_res_art")
        private val SHOW_WAVEFORM_KEY = booleanPreferencesKey("show_waveform")
        private val AUTO_RESUME_HEADSET_KEY = booleanPreferencesKey("auto_resume_headset")
        private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        private val SHOW_LOCK_SCREEN_CONTROLS_KEY = booleanPreferencesKey("show_lock_screen_controls")
        private val CONTINUE_TO_NEXT_FOLDER_KEY = booleanPreferencesKey("continue_to_next_folder")
    }

    val darkMode: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[DARK_MODE_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val scanFolders: StateFlow<Set<String>> = musicRepository.getScanFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val gaplessPlayback: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[GAPLESS_PLAYBACK_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val crossfadeEnabled: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[CROSSFADE_ENABLED_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val crossfadeDuration: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[CROSSFADE_DURATION_KEY] ?: 3 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 3)

    val showAlbumArt: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[SHOW_ALBUM_ART_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val highResArt: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[HIGH_RES_ART_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val showWaveform: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[SHOW_WAVEFORM_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val autoResumeOnHeadset: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[AUTO_RESUME_HEADSET_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val keepScreenOn: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEEP_SCREEN_ON_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val showLockScreenControls: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[SHOW_LOCK_SCREEN_CONTROLS_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val continueToNextFolder: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[CONTINUE_TO_NEXT_FOLDER_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun toggleDarkMode() = toggleBoolPref(DARK_MODE_KEY, false)
    fun toggleGaplessPlayback() = toggleBoolPref(GAPLESS_PLAYBACK_KEY, true)
    fun toggleCrossfade() = toggleBoolPref(CROSSFADE_ENABLED_KEY, false)
    fun toggleShowAlbumArt() = toggleBoolPref(SHOW_ALBUM_ART_KEY, true)
    fun toggleHighResArt() = toggleBoolPref(HIGH_RES_ART_KEY, false)
    fun toggleShowWaveform() = toggleBoolPref(SHOW_WAVEFORM_KEY, true)
    fun toggleAutoResumeOnHeadset() = toggleBoolPref(AUTO_RESUME_HEADSET_KEY, false)
    fun toggleKeepScreenOn() = toggleBoolPref(KEEP_SCREEN_ON_KEY, false)
    fun toggleShowLockScreenControls() = toggleBoolPref(SHOW_LOCK_SCREEN_CONTROLS_KEY, true)
    fun toggleContinueToNextFolder() = toggleBoolPref(CONTINUE_TO_NEXT_FOLDER_KEY, false)

    private fun toggleBoolPref(key: Preferences.Key<Boolean>, default: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[key] = !(prefs[key] ?: default)
            }
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[CROSSFADE_DURATION_KEY] = seconds
            }
        }
    }

    fun addScanFolder(path: String, treeUri: String? = null) {
        viewModelScope.launch {
            musicRepository.addScanFolder(path)
            if (treeUri != null) {
                musicRepository.addScanFolderUri(path, treeUri)
            }
            musicRepository.refreshLibrary(force = true)
        }
    }

    fun removeScanFolder(path: String) {
        viewModelScope.launch {
            musicRepository.removeScanFolder(path)
            musicRepository.refreshLibrary(force = true)
        }
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            musicRepository.refreshLibrary(force = true)
        }
    }

    fun exportSettings() {
        // Placeholder - would use content resolver to write JSON
    }

    fun importSettings() {
        // Placeholder - would use content resolver to read JSON
    }
}
