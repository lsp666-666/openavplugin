package com.openavplugin.provider.video

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.openavplugin.provider.VideoConfig
import com.openavplugin.provider.VideoSource
import com.openavplugin.provider.VideoSource.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class LocalFileVideoSource : VideoSource {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var isInitialized = false
    private var videoWidth = 0
    private var videoHeight = 0
    private val bufferInfo = MediaCodec.BufferInfo()

    override suspend fun initialize(config: VideoConfig) = withContext(Dispatchers.IO) {
        val path = config.sourcePath ?: throw IllegalArgumentException("Source path required")

        val ext = MediaExtractor()
        ext.setDataSource(path)

        val trackIndex = findVideoTrack(ext)
        if (trackIndex < 0) throw IllegalStateException("No video track found")

        ext.selectTrack(trackIndex)
        val format = ext.getTrackFormat(trackIndex)

        videoWidth = format.getInteger(MediaFormat.KEY_WIDTH)
        videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
        val mimeType = format.getString(MediaFormat.KEY_MIME)!!

        val dec = MediaCodec.createDecoderByType(mimeType)
        dec.configure(format, null, null, 0)
        dec.start()

        extractor = ext
        codec = dec
        isInitialized = true
    }

    override suspend fun getNextFrame(): Frame? = withContext(Dispatchers.IO) {
        val ext = extractor ?: return@withContext null
        val dec = codec ?: return@withContext null

        // Feed input
        val inputIndex = dec.dequeueInputBuffer(10000)
        if (inputIndex >= 0) {
            val inputBuffer = dec.getInputBuffer(inputIndex) ?: return@withContext null
            val sampleSize = ext.readSampleData(inputBuffer, 0)
            if (sampleSize > 0) {
                dec.queueInputBuffer(inputIndex, 0, sampleSize, ext.sampleTime, 0)
                ext.advance()
            } else {
                // Loop back to start
                ext.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val retrySample = ext.readSampleData(inputBuffer, 0)
                if (retrySample > 0) {
                    dec.queueInputBuffer(inputIndex, 0, retrySample, 0, 0)
                    ext.advance()
                }
            }
        }

        // Get output
        val outputIndex = dec.dequeueOutputBuffer(bufferInfo, 10000)
        if (outputIndex >= 0) {
            val outputBuffer = dec.getOutputBuffer(outputIndex) ?: return@withContext null
            val frameData = ByteArray(bufferInfo.size)
            outputBuffer.get(frameData)
            dec.releaseOutputBuffer(outputIndex, false)

            return@withContext Frame(
                data = frameData,
                width = videoWidth,
                height = videoHeight,
                format = ImageFormat.NV21
            )
        }

        null
    }

    override suspend fun release() {
        codec?.stop()
        codec?.release()
        extractor?.release()
        codec = null
        extractor = null
        isInitialized = false
    }

    override fun isReady(): Boolean = isInitialized

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) return i
        }
        return -1
    }
}
