package com.openavplugin.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val cameraEnabled: Boolean = false,
    val cameraSourceType: SourceType = SourceType.NONE,
    val cameraSourcePath: String? = null,
    val cameraResolution: String = "1280x720",
    val micEnabled: Boolean = false,
    val micSourceType: SourceType = SourceType.NONE,
    val micSourcePath: String? = null,
    val micSampleRate: Int = 44100,
    val micChannels: Int = 1,
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    companion object {
        fun withTimestamps(
            packageName: String,
            appName: String,
            cameraEnabled: Boolean = false,
            cameraSourceType: SourceType = SourceType.NONE,
            cameraSourcePath: String? = null,
            cameraResolution: String = "1280x720",
            micEnabled: Boolean = false,
            micSourceType: SourceType = SourceType.NONE,
            micSourcePath: String? = null,
            micSampleRate: Int = 44100,
            micChannels: Int = 1,
            isActive: Boolean = true
        ): AppRule {
            val now = System.currentTimeMillis()
            return AppRule(
                packageName = packageName,
                appName = appName,
                cameraEnabled = cameraEnabled,
                cameraSourceType = cameraSourceType,
                cameraSourcePath = cameraSourcePath,
                cameraResolution = cameraResolution,
                micEnabled = micEnabled,
                micSourceType = micSourceType,
                micSourcePath = micSourcePath,
                micSampleRate = micSampleRate,
                micChannels = micChannels,
                isActive = isActive,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

enum class SourceType {
    NONE,
    SILENCE,
    LOCAL_VIDEO,
    LOCAL_AUDIO,
    NETWORK_STREAM,
    SCREEN_CAPTURE,
    SYSTEM_AUDIO,
    CAMERA_BLOCK
}
