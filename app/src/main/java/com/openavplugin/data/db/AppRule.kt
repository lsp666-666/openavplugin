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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SourceType {
    NONE,
    SILENCE,
    LOCAL_VIDEO,
    LOCAL_AUDIO,
    NETWORK_STREAM,
    SCREEN_CAPTURE,
    SYSTEM_AUDIO
}
