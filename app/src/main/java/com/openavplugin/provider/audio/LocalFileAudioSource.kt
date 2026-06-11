package com.openavplugin.provider.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.openavplugin.provider.AudioConfig
import com.openavplugin.provider.AudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class LocalFileAudioSource(private val context: Context) : AudioSource {
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var isInitialized = false
    private val bufferInfo = MediaCodec.BufferInfo()
    private var sampleRate = 44100
    private var channels = 1

    override suspend fun initialize(config: AudioConfig) = withContext(Dispatchers.IO) {
        val path = config.sourcePath ?: throw IllegalArgumentException("Source path required")

        val ext = MediaExtractor()
        ext.setDataSource(path)

        val trackIndex = findAudioTrack(ext)
        if (trackIndex < 0) throw IllegalStateException("No audio track found")

        ext.selectTrack(trackIndex)
        val format = ext.getTrackFormat(trackIndex)

        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mimeType = format.getString(MediaFormat.KEY_MIME)!!

        val dec = MediaCodec.createDecoderByType(mimeType)
        dec.configure(format, null, null, 0)
        dec.start()

        extractor = ext
        codec = dec
        isInitialized = true
    }

    override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int =
        withContext(Dispatchers.IO) {
            val ext = extractor ?: return@withContext 0
            val dec = codec ?: return@withContext 0

            // Feed input
            val inputIndex = dec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)!!
                val sampleSize = ext.readSampleData(inputBuffer, 0)
                if (sampleSize > 0) {
                    dec.queueInputBuffer(inputIndex, 0, sampleSize, ext.sampleTime, 0)
                    ext.advance()
                } else {
                    ext.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }
            }

            // Get output
            val outputIndex = dec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                val outputBuffer = dec.getOutputBuffer(outputIndex)
                val bytesToRead = minOf(size, bufferInfo.size)
                outputBuffer?.get(buffer, offset, bytesToRead)
                dec.releaseOutputBuffer(outputIndex, false)
                return@withContext bytesToRead
            }

            0
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

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }
}
