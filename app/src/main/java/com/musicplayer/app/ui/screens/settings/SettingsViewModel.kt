package com.musicplayer.app.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        val SHOW_WAVEFORM_KEY = booleanPreferencesKey("show_waveform")
        val AUTO_RESUME_HEADSET_KEY = booleanPreferencesKey("auto_resume_headset")
        val RESUME_ON_APP_FOREGROUND_KEY = booleanPreferencesKey("resume_on_app_foreground")
        private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        private val CONTINUE_TO_NEXT_FOLDER_KEY = booleanPreferencesKey("continue_to_next_folder")
    }

    val scanFolders: StateFlow<Set<String>> = musicRepository.getScanFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val showWaveform: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[SHOW_WAVEFORM_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val autoResumeOnHeadset: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[AUTO_RESUME_HEADSET_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val resumeOnAppForeground: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[RESUME_ON_APP_FOREGROUND_KEY] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val keepScreenOn: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEEP_SCREEN_ON_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val continueToNextFolder: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[CONTINUE_TO_NEXT_FOLDER_KEY] ?: false }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun toggleShowWaveform() = toggleBoolPref(SHOW_WAVEFORM_KEY, true)
    fun toggleAutoResumeOnHeadset() = toggleBoolPref(AUTO_RESUME_HEADSET_KEY, false)
    fun toggleResumeOnAppForeground() = toggleBoolPref(RESUME_ON_APP_FOREGROUND_KEY, true)
    fun toggleKeepScreenOn() = toggleBoolPref(KEEP_SCREEN_ON_KEY, false)
    fun toggleContinueToNextFolder() = toggleBoolPref(CONTINUE_TO_NEXT_FOLDER_KEY, false)

    private fun toggleBoolPref(key: Preferences.Key<Boolean>, default: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[key] = !(prefs[key] ?: default)
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
}
