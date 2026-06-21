package com.musicplayer.app.player

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.IntentCompat

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
                val device = IntentCompat.getParcelableExtra(
                    intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                )
                // Reading bluetoothClass requires BLUETOOTH_CONNECT on API 31+; if the
                // permission was denied this throws SecurityException and the class is
                // unreadable. Rather than blindly resuming for ANY device (which would
                // wake playback when a mouse/keyboard/watch connects), fall back to
                // checking whether a Bluetooth *audio* output is actually present — that
                // check needs no permission.
                val majorClass = try {
                    device?.bluetoothClass?.majorDeviceClass
                } catch (_: SecurityException) {
                    null
                }
                val isAudio = when (majorClass) {
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> true
                    null -> context?.let { hasBluetoothAudioOutput(it) } ?: false
                    else -> false
                }
                if (isAudio) {
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

    /**
     * True if a Bluetooth audio output (A2DP / SCO / LE headset) is currently routed.
     * Uses AudioManager, which requires no Bluetooth permission, so it works as a
     * fallback when the connected device's class cannot be read.
     */
    private fun hasBluetoothAudioOutput(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
