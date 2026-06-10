package com.musicplayer.app.player.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val EQ_ENABLED_KEY = booleanPreferencesKey("eq_enabled")
        private val EQ_PRESET_KEY = intPreferencesKey("eq_preset")
        private val EQ_BANDS_KEY = stringPreferencesKey("eq_bands")
        private val BASS_BOOST_KEY = intPreferencesKey("bass_boost_strength")
        private val PREAMP_KEY = floatPreferencesKey("eq_preamp")
        private val TONE_BASS_KEY = floatPreferencesKey("eq_tone_bass")
        private val TONE_TREBLE_KEY = floatPreferencesKey("eq_tone_treble")
        private val LIMITER_KEY = floatPreferencesKey("eq_limiter")
        private val ACTIVE_TAB_KEY = intPreferencesKey("eq_active_tab")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bandLevels = MutableStateFlow<List<Int>>(emptyList())
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()

    private val _bandFrequencies = MutableStateFlow<List<Int>>(emptyList())
    val bandFrequencies: StateFlow<List<Int>> = _bandFrequencies.asStateFlow()

    private val _presetNames = MutableStateFlow<List<String>>(emptyList())
    val presetNames: StateFlow<List<String>> = _presetNames.asStateFlow()

    private val _currentPreset = MutableStateFlow(-1)
    val currentPreset: StateFlow<Int> = _currentPreset.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _minBandLevel = MutableStateFlow(-1500)
    val minBandLevel: StateFlow<Int> = _minBandLevel.asStateFlow()

    private val _maxBandLevel = MutableStateFlow(1500)
    val maxBandLevel: StateFlow<Int> = _maxBandLevel.asStateFlow()

    private val _preampLevel = MutableStateFlow(0f)
    val preampLevel: StateFlow<Float> = _preampLevel.asStateFlow()

    private val _toneBass = MutableStateFlow(50f)
    val toneBass: StateFlow<Float> = _toneBass.asStateFlow()

    private val _toneTreble = MutableStateFlow(50f)
    val toneTreble: StateFlow<Float> = _toneTreble.asStateFlow()

    private val _limiterStrength = MutableStateFlow(0f)
    val limiterStrength: StateFlow<Float> = _limiterStrength.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    @Synchronized
    fun initialize(audioSessionId: Int) {
        release()
        try {
            val eq = Equalizer(0, audioSessionId).apply {
                val numBands = numberOfBands.toInt()
                val range = bandLevelRange
                _minBandLevel.value = range[0].toInt()
                _maxBandLevel.value = range[1].toInt()

                val freqs = (0 until numBands).map { getCenterFreq(it.toShort()) / 1000 }
                _bandFrequencies.value = freqs

                val presets = (0 until numberOfPresets.toInt()).map {
                    getPresetName(it.toShort())
                }
                _presetNames.value = presets
            }
            equalizer = eq
            bassBoost = BassBoost(0, audioSessionId).apply {
                scope.launch { restoreBassBoost(this@apply) }
            }
            // Restore base bands + preamp/tone scalars, THEN apply the combined effect
            // to the fresh hardware. Sequencing matters (bands before offsets), and this
            // re-applies preamp/tone after an audio-session-id change instead of silently
            // dropping them — they previously only ever lived on the released Equalizer.
            scope.launch {
                restoreSettings(eq)
                restoreExtendedSettings()
                applyEffects()
            }
        } catch (_: Exception) {
            // EQ not supported on some devices
        }
    }

    private suspend fun restoreSettings(eq: Equalizer) {
        val prefs = dataStore.data.first()
        val enabled = prefs[EQ_ENABLED_KEY] ?: false
        eq.enabled = enabled
        _isEnabled.value = enabled

        val savedBands = prefs[EQ_BANDS_KEY]
        if (savedBands != null) {
            val levels = savedBands.split(",").mapNotNull { it.toIntOrNull() }
            levels.forEachIndexed { index, level ->
                if (index < eq.numberOfBands) {
                    eq.setBandLevel(index.toShort(), level.toShort())
                }
            }
            _bandLevels.value = levels
        } else {
            _bandLevels.value = (0 until eq.numberOfBands.toInt()).map {
                eq.getBandLevel(it.toShort()).toInt()
            }
        }

        val preset = prefs[EQ_PRESET_KEY] ?: -1
        _currentPreset.value = preset
    }

    private suspend fun restoreBassBoost(bb: BassBoost) {
        val prefs = dataStore.data.first()
        val strength = prefs[BASS_BOOST_KEY] ?: 0
        bb.enabled = strength > 0
        bb.setStrength(strength.toShort())
        _bassBoostStrength.value = strength
    }

    private suspend fun restoreExtendedSettings() {
        val prefs = dataStore.data.first()
        _preampLevel.value = prefs[PREAMP_KEY] ?: 0f
        _toneBass.value = prefs[TONE_BASS_KEY] ?: 50f
        _toneTreble.value = prefs[TONE_TREBLE_KEY] ?: 50f
        _limiterStrength.value = prefs[LIMITER_KEY] ?: 0f
        _activeTab.value = prefs[ACTIVE_TAB_KEY] ?: 0
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        _isEnabled.value = enabled
        scope.launch {
            dataStore.edit { it[EQ_ENABLED_KEY] = enabled }
        }
    }

    fun setBandLevel(band: Int, level: Int) {
        _bandLevels.update { current ->
            if (band < current.size) {
                current.toMutableList().also { it[band] = level }
            } else current
        }
        _currentPreset.value = -1
        applyEffects()
        scope.launch {
            dataStore.edit { it[EQ_BANDS_KEY] = _bandLevels.value.joinToString(",") }
        }
    }

    fun usePreset(presetIndex: Int) {
        equalizer?.let { eq ->
            eq.usePreset(presetIndex.toShort())
            _currentPreset.value = presetIndex
            // Read the preset's band levels as the new base, then re-layer preamp/tone.
            val levels = (0 until eq.numberOfBands.toInt()).map {
                eq.getBandLevel(it.toShort()).toInt()
            }
            _bandLevels.value = levels
            applyEffects()
            scope.launch {
                dataStore.edit {
                    it[EQ_PRESET_KEY] = presetIndex
                    it[EQ_BANDS_KEY] = levels.joinToString(",")
                }
            }
        }
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        bassBoost?.let { bb ->
            bb.enabled = clamped > 0
            bb.setStrength(clamped.toShort())
            _bassBoostStrength.value = clamped
            scope.launch {
                dataStore.edit { it[BASS_BOOST_KEY] = clamped }
            }
        }
    }

    fun setPreampLevel(level: Float) {
        _preampLevel.value = level
        applyEffects()
        scope.launch {
            dataStore.edit { it[PREAMP_KEY] = level }
        }
    }

    fun setToneBass(percent: Float) {
        _toneBass.value = percent
        applyEffects()
        scope.launch {
            dataStore.edit { it[TONE_BASS_KEY] = percent }
        }
    }

    fun setToneTreble(percent: Float) {
        _toneTreble.value = percent
        applyEffects()
        scope.launch {
            dataStore.edit { it[TONE_TREBLE_KEY] = percent }
        }
    }

    /**
     * Apply the full effect chain to the hardware in one pass: each band =
     * base level (`_bandLevels`) + preamp offset + tone offset, clamped to range.
     * Previously preamp and tone were applied independently and each read the base
     * levels, so whichever ran last overwrote the other on the hardware. Computing the
     * combined value here makes them additive and idempotent, and lets `initialize`
     * re-apply them after a session change.
     */
    private fun applyEffects() {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        if (numBands == 0) return
        val preampOffset = (_preampLevel.value * 100).toInt() // dB -> millibels
        val bassOffset = ((_toneBass.value - 50f) / 50f * _maxBandLevel.value).toInt()
        val trebleOffset = ((_toneTreble.value - 50f) / 50f * _maxBandLevel.value).toInt()
        val midPoint = numBands / 2
        for (i in 0 until numBands) {
            val base = _bandLevels.value.getOrElse(i) { 0 }
            val tone = if (numBands >= 2) (if (i < midPoint) bassOffset else trebleOffset) else 0
            val level = (base + preampOffset + tone)
                .coerceIn(_minBandLevel.value, _maxBandLevel.value)
            eq.setBandLevel(i.toShort(), level.toShort())
        }
    }

    fun setLimiterStrength(strength: Float) {
        _limiterStrength.value = strength
        scope.launch {
            dataStore.edit { it[LIMITER_KEY] = strength }
        }
    }

    fun setActiveTab(tab: Int) {
        _activeTab.value = tab
        scope.launch {
            dataStore.edit { it[ACTIVE_TAB_KEY] = tab }
        }
    }

    @Synchronized
    fun release() {
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null
    }
}
