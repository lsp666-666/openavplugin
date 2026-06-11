package com.openavplugin.provider

import android.graphics.ImageFormat
import java.nio.ByteBuffer

interface VideoSource {
    data class Frame(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val format: Int = ImageFormat.NV21,
        val timestamp: Long = System.nanoTime()
    )

    suspend fun initialize(config: VideoConfig)
    suspend fun getNextFrame(): Frame?
    suspend fun release()
    fun isReady(): Boolean
}

data class VideoConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val sourcePath: String? = null,
    val streamUrl: String? = null
)
