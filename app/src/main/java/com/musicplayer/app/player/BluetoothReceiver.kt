package com.musicplayer.app.player

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * BroadcastReceiver that auto-resumes playback when a Bluetooth audio device
 * or wired headset is connected, if the setting is enabled.
 */
class BluetoothReceiver(
    private val onDeviceConnected: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // Only resume for audio Bluetooth devices, not mice/keyboards/etc.
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // Reading bluetoothClass requires BLUETOOTH_CONNECT on API 31+; if the
                // permission was denied this can throw a SecurityException. Treat an
                // unreadable class as "assume audio" so resume still works rather than
                // silently dropping every connect event.
                val majorClass = try {
                    device?.bluetoothClass?.majorDeviceClass
                } catch (_: SecurityException) {
                    null
                }
                if (majorClass == null || majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    onDeviceConnected()
                }
            }
            AudioManager.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", 0)
                if (state == 1) { // plugged in
                    onDeviceConnected()
                }
            }
        }
    }
}
