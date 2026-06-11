package com.openavplugin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openavplugin_config")

enum class RuntimeMode {
    AUTO,
    LSPOSED,
    ROOT
}

class ConfigManager(private val context: Context) {
    companion object {
        val GLOBAL_CAMERA_ENABLED = booleanPreferencesKey("global_camera_enabled")
        val GLOBAL_MIC_ENABLED = booleanPreferencesKey("global_mic_enabled")
        val RUNTIME_MODE = stringPreferencesKey("runtime_mode")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val BOOT_AUTO_START = booleanPreferencesKey("boot_auto_start")
    }

    private val dataStore = context.dataStore

    val globalCameraEnabled: Flow<Boolean> = dataStore.data
        .map { it[GLOBAL_CAMERA_ENABLED] ?: false }

    val globalMicEnabled: Flow<Boolean> = dataStore.data
        .map { it[GLOBAL_MIC_ENABLED] ?: false }

    val runtimeMode: Flow<RuntimeMode> = dataStore.data
        .map { prefs ->
            val modeString = prefs[RUNTIME_MODE]
            when (modeString) {
                "LSPOSED" -> RuntimeMode.LSPOSED
                "ROOT" -> RuntimeMode.ROOT
                else -> RuntimeMode.AUTO
            }
        }

    val logLevel: Flow<String> = dataStore.data
        .map { it[LOG_LEVEL] ?: "INFO" }

    val bootAutoStart: Flow<Boolean> = dataStore.data
        .map { it[BOOT_AUTO_START] ?: true }

    suspend fun setGlobalCameraEnabled(enabled: Boolean) {
        dataStore.edit { it[GLOBAL_CAMERA_ENABLED] = enabled }
    }

    suspend fun setGlobalMicEnabled(enabled: Boolean) {
        dataStore.edit { it[GLOBAL_MIC_ENABLED] = enabled }
    }

    suspend fun setRuntimeMode(mode: RuntimeMode) {
        dataStore.edit { prefs ->
            prefs[RUNTIME_MODE] = mode.name
        }
    }

    suspend fun setLogLevel(level: String) {
        dataStore.edit { it[LOG_LEVEL] = level }
    }

    suspend fun setBootAutoStart(enabled: Boolean) {
        dataStore.edit { it[BOOT_AUTO_START] = enabled }
    }

    fun getRuntimeModeSync(): RuntimeMode {
        return runBlocking {
            dataStore.data.first()[RUNTIME_MODE]?.let { modeString ->
                when (modeString) {
                    "LSPOSED" -> RuntimeMode.LSPOSED
                    "ROOT" -> RuntimeMode.ROOT
                    else -> RuntimeMode.AUTO
                }
            } ?: RuntimeMode.AUTO
        }
    }
}
