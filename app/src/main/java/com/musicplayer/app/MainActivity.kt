package com.musicplayer.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.musicplayer.app.player.PlaybackController
import com.musicplayer.app.player.service.PlaybackService
import com.musicplayer.app.ui.MusicPlayerRoot
import com.musicplayer.app.ui.screens.settings.SettingsViewModel
import com.musicplayer.app.ui.theme.MusicPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private val _navigateToNowPlaying = MutableStateFlow(false)
        val navigateToNowPlaying: StateFlow<Boolean> = _navigateToNowPlaying.asStateFlow()

        fun consumeNowPlayingNavigation() {
            _navigateToNowPlaying.value = false
        }

        private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
    }

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var playbackController: PlaybackController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (results[audioPermission] == false) {
            Toast.makeText(this, "Storage permission is required to load music", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()
        handleIntent(intent)
        observeKeepScreenOn()

        setContent {
            MusicPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayerRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        maybeResumeOnForeground()
    }

    private fun maybeResumeOnForeground() {
        // Resume paused playback when the app is foregrounded while a Bluetooth audio
        // device is currently connected.  Gated by a user setting (default on) and only
        // fires when there's a queue but the player is paused.
        lifecycleScope.launch {
            val enabled = dataStore.data
                .map { it[SettingsViewModel.RESUME_ON_APP_FOREGROUND_KEY] ?: true }
                .first()
            if (!enabled) return@launch
            if (!isBluetoothAudioConnected()) return@launch
            val state = playbackController.playbackState.value
            if (state.currentSong != null && !state.isPlaying) {
                playbackController.play()
            }
        }
    }

    private fun isBluetoothAudioConnected(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == PlaybackService.ACTION_OPEN_NOW_PLAYING) {
            _navigateToNowPlaying.value = true
        }
    }

    private fun observeKeepScreenOn() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.data
                    .map { prefs -> prefs[KEEP_SCREEN_ON_KEY] ?: false }
                    .distinctUntilChanged()
                    .collectLatest { keepOn ->
                        if (keepOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
