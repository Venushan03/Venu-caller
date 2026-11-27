package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.VoicePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "prismcall_settings")

data class PrismSettings(
    val hapticDialKeys: Boolean = true,
    val dtmfTonesEnabled: Boolean = true,
    val flashStrobeOnIncoming: Boolean = false,
    val autoScreenFailedStirShaken: Boolean = true,
    val selectedVoicePreset: VoicePreset = VoicePreset.ORIGINAL,
    val speedDialSlot1: String = "",
    val speedDialSlot2: String = "",
    val speedDialSlot3: String = ""
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val HAPTIC = booleanPreferencesKey("haptic_dial_keys")
        val DTMF = booleanPreferencesKey("dtmf_tones_enabled")
        val FLASH_STROBE = booleanPreferencesKey("flash_strobe_incoming")
        val AUTO_SCREEN = booleanPreferencesKey("auto_screen_spam")
        val VOICE_PRESET = stringPreferencesKey("selected_voice_preset")
        val SPEED_1 = stringPreferencesKey("speed_dial_1")
        val SPEED_2 = stringPreferencesKey("speed_dial_2")
        val SPEED_3 = stringPreferencesKey("speed_dial_3")
    }

    val settingsFlow: Flow<PrismSettings> = context.dataStore.data.map { prefs ->
        val rawPreset = prefs[Keys.VOICE_PRESET]
        val preset = VoicePreset.entries.firstOrNull { it.name == rawPreset } ?: VoicePreset.ORIGINAL
        PrismSettings(
            hapticDialKeys = prefs[Keys.HAPTIC] ?: true,
            dtmfTonesEnabled = prefs[Keys.DTMF] ?: true,
            flashStrobeOnIncoming = prefs[Keys.FLASH_STROBE] ?: false,
            autoScreenFailedStirShaken = prefs[Keys.AUTO_SCREEN] ?: true,
            selectedVoicePreset = preset,
            speedDialSlot1 = prefs[Keys.SPEED_1] ?: "",
            speedDialSlot2 = prefs[Keys.SPEED_2] ?: "",
            speedDialSlot3 = prefs[Keys.SPEED_3] ?: ""
        )
    }

    suspend fun setHapticDialKeys(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC] = enabled }
    }

    suspend fun setDtmfTones(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DTMF] = enabled }
    }

    suspend fun setFlashStrobe(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FLASH_STROBE] = enabled }
    }

    suspend fun setAutoScreenSpam(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SCREEN] = enabled }
    }

    suspend fun setVoicePreset(preset: VoicePreset) {
        context.dataStore.edit { it[Keys.VOICE_PRESET] = preset.name }
    }

    suspend fun setSpeedDial(slot: Int, number: String) {
        context.dataStore.edit { prefs ->
            when (slot) {
                1 -> prefs[Keys.SPEED_1] = number
                2 -> prefs[Keys.SPEED_2] = number
                3 -> prefs[Keys.SPEED_3] = number
            }
        }
    }
}
