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
                val majorClass = device?.bluetoothClass?.majorDeviceClass
                if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
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
