package com.openavplugin.provider.video

import android.graphics.ImageFormat
import com.openavplugin.provider.VideoConfig
import com.openavplugin.provider.VideoSource
import com.openavplugin.provider.VideoSource.Frame

/**
 * Privacy-focused video source that always returns null frames.
 * Used when the user wants to block camera output entirely (privacy mode).
 */
class BlockVideoSource : VideoSource {
    private var isInitialized = false

    override suspend fun initialize(config: VideoConfig) {
        isInitialized = true
    }

    override suspend fun getNextFrame(): Frame? {
        return null // Always return null — camera sees nothing
    }

    override suspend fun release() {
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized
}
