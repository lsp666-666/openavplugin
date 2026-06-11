package com.openavplugin.provider

interface AudioSource {
    data class AudioBuffer(
        val data: ByteArray,
        val sampleRate: Int,
        val channels: Int,
        val timestamp: Long = System.nanoTime()
    )

    suspend fun initialize(config: AudioConfig)
    suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int
    suspend fun release()
    fun isReady(): Boolean
}

data class AudioConfig(
    val sampleRate: Int = 44100,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val sourcePath: String? = null
)
