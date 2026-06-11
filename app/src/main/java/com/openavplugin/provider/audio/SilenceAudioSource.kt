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
        buffer.fill(0, offset, offset + size)
        return size
    }

    override suspend fun release() {
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
