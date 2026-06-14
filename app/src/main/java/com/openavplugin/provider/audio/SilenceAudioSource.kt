package com.openavplugin.provider.audio

import com.openavplugin.provider.AudioConfig
import com.openavplugin.provider.AudioSource

class SilenceAudioSource : AudioSource {
    private var isInitialized = false
    private var sampleRate = 44100
    private var channels = 1

    override suspend fun initialize(config: AudioConfig) {
        isInitialized = true
        sampleRate = config.sampleRate
        channels = config.channels
    }

    override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int {
        if (!isInitialized) return 0
        val safeOffset = offset.coerceIn(0, buffer.size)
        val safeEnd = (offset + size).coerceIn(safeOffset, buffer.size)
        buffer.fill(0, safeOffset, safeEnd)
        val bytesRead = safeEnd - safeOffset
        // Simulate real-time recording speed:
        // buffer size / (sampleRate * channels * 2 bytes/sample) = seconds
        val durationMs = bytesRead * 1000L / (sampleRate * channels * 2)
        if (durationMs > 0) kotlinx.coroutines.delay(durationMs)
        return bytesRead
    }

    override suspend fun release() {
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
