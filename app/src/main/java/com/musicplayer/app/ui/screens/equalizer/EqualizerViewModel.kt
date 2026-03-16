package com.musicplayer.app.ui.screens.equalizer

import androidx.lifecycle.ViewModel
import com.musicplayer.app.player.audio.EqualizerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    val equalizerManager: EqualizerManager
) : ViewModel() {

    val isEnabled = equalizerManager.isEnabled
    val bandLevels = equalizerManager.bandLevels
    val bandFrequencies = equalizerManager.bandFrequencies
    val presetNames = equalizerManager.presetNames
    val currentPreset = equalizerManager.currentPreset
    val bassBoostStrength = equalizerManager.bassBoostStrength
    val minBandLevel = equalizerManager.minBandLevel
    val maxBandLevel = equalizerManager.maxBandLevel
    val preampLevel = equalizerManager.preampLevel
    val toneBass = equalizerManager.toneBass
    val toneTreble = equalizerManager.toneTreble
    val limiterStrength = equalizerManager.limiterStrength
    val activeTab = equalizerManager.activeTab

    fun setEnabled(enabled: Boolean) = equalizerManager.setEnabled(enabled)
    fun setBandLevel(band: Int, level: Int) = equalizerManager.setBandLevel(band, level)
    fun usePreset(presetIndex: Int) = equalizerManager.usePreset(presetIndex)
    fun setBassBoost(strength: Int) = equalizerManager.setBassBoost(strength)
    fun setPreampLevel(level: Float) = equalizerManager.setPreampLevel(level)
    fun setToneBass(percent: Float) = equalizerManager.setToneBass(percent)
    fun setToneTreble(percent: Float) = equalizerManager.setToneTreble(percent)
    fun setLimiterStrength(strength: Float) = equalizerManager.setLimiterStrength(strength)
    fun setActiveTab(tab: Int) = equalizerManager.setActiveTab(tab)
}
