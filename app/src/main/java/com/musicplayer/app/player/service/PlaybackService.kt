package com.musicplayer.app.player.service

import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Bundle
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musicplayer.app.MainActivity
import com.musicplayer.app.R
import com.musicplayer.app.player.BluetoothReceiver
import com.musicplayer.app.player.audio.EqualizerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_OPEN_NOW_PLAYING = "com.musicplayer.app.OPEN_NOW_PLAYING"
        private const val COMMAND_STOP_SERVICE = "com.musicplayer.app.STOP_SERVICE"
        private val _audioSessionId = MutableStateFlow(0)
        val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()
        private val AUTO_RESUME_KEY = booleanPreferencesKey("auto_resume_on_headset")
    }

    @Inject
    lateinit var equalizerManager: EqualizerManager

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private var mediaSession: MediaSession? = null
    private var bluetoothReceiver: BluetoothReceiver? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastArtworkMediaId: String? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val sessionId = player.audioSessionId
        _audioSessionId.value = sessionId
        if (sessionId != 0) {
            equalizerManager.initialize(sessionId)
        }

        // Set up notification click to open NowPlaying
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOW_PLAYING
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Close button shown in the notification
        val closeButton = CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(SessionCommand(COMMAND_STOP_SERVICE, Bundle.EMPTY))
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(listOf(closeButton))
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == COMMAND_STOP_SERVICE) {
                        session.player.stop()
                        session.player.clearMediaItems()
                        stopSelf()
                    }
                    return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
                }
            })
            .build()

        // Update notification artwork with per-song embedded art on track change
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem ?: return
                val mediaId = mediaItem.mediaId
                if (mediaId == lastArtworkMediaId) return
                serviceScope.launch(Dispatchers.IO) {
                    updateNotificationArtwork(player, mediaItem)
                }
            }
        })

        // Register Bluetooth/headset receiver for auto-resume
        bluetoothReceiver = BluetoothReceiver {
            serviceScope.launch {
                val prefs = dataStore.data.first()
                val autoResume = prefs[AUTO_RESUME_KEY] ?: true
                if (autoResume) {
                    val p = mediaSession?.player
                    if (p != null && p.mediaItemCount > 0 && !p.isPlaying) {
                        p.play()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private fun updateNotificationArtwork(player: Player, mediaItem: MediaItem) {
        val uri = mediaItem.localConfiguration?.uri ?: return
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val updatedMetadata = mediaItem.mediaMetadata.buildUpon()
                    .setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
                val updatedItem = mediaItem.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()
                serviceScope.launch(Dispatchers.Main) {
                    lastArtworkMediaId = mediaItem.mediaId
                    val index = player.currentMediaItemIndex
                    player.replaceMediaItem(index, updatedItem)
                }
            } else {
                lastArtworkMediaId = mediaItem.mediaId
            }
        } catch (_: Exception) {
            lastArtworkMediaId = mediaItem.mediaId
        } finally {
            retriever.release()
        }
    }

    override fun onDestroy() {
        bluetoothReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        bluetoothReceiver = null
        equalizerManager.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
