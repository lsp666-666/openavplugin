package com.openavplugin.provider.audio

import com.openavplugin.provider.AudioConfig
import com.openavplugin.provider.AudioSource

class SilenceAudioSource : AudioSource {
    private var isInitialized = false

    override suspend fun initialize(config: AudioConfig) {
        isInitialized = true
    }

    override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int {
        if (!isInitialized) return 0
        val safeOffset = offset.coerceIn(0, buffer.size)
        val safeEnd = (offset + size).coerceIn(safeOffset, buffer.size)
        buffer.fill(0, safeOffset, safeEnd)
        return safeEnd - safeOffset
    }

    override suspend fun release() {
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
