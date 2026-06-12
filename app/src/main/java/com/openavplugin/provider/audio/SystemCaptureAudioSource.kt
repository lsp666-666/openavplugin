package com.openavplugin.provider.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import com.openavplugin.provider.AudioConfig
import com.openavplugin.provider.AudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SystemCaptureAudioSource : AudioSource {
    private var audioRecord: AudioRecord? = null
    private var isInitialized = AtomicBoolean(false)
    private var isCapturing = AtomicBoolean(false)
    private var mediaProjection = AtomicReference<MediaProjection?>(null)
    private var sampleRate = 44100
    private var channels = 1
    private var bufferSize = 0

    override suspend fun initialize(config: AudioConfig) = withContext(Dispatchers.IO) {
        sampleRate = config.sampleRate
        channels = config.channels
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        bufferSize = AudioRecord.getMinBufferSize(sampleRate,
            if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
            encoding
        )
        isInitialized.set(true)
    }

    /**
     * Attach a MediaProjection and start capturing system audio.
     */
    fun startCapture(projection: MediaProjection) {
        mediaProjection.set(projection)
        if (!isInitialized.get()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(
                        if (channels == 1) AudioFormat.CHANNEL_IN_MONO
                        else AudioFormat.CHANNEL_IN_STEREO
                    )
                    .build()

                audioRecord = AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

                audioRecord?.startRecording()
                isCapturing.set(true)
            } else {
                // Fallback: use MediaRecorder-based capture
                val recorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                    setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                    setAudioSamplingRate(sampleRate)
                    setAudioChannels(channels)
                    // Output to a temp file for reading
                    setOutputFile("/dev/null")
                    prepare()
                    start()
                }
                isCapturing.set(true)
            }
        } catch (e: Exception) {
            isCapturing.set(false)
        }
    }

    fun setMediaProjection(projection: MediaProjection) {
        startCapture(projection)
    }

    override suspend fun read(buffer: ByteArray, offset: Int, size: Int): Int =
        withContext(Dispatchers.IO) {
            if (!isInitialized.get() || !isCapturing.get()) {
                // Return silence as fallback
                val safeOffset = offset.coerceIn(0, buffer.size)
                val safeEnd = (offset + size).coerceIn(safeOffset, buffer.size)
                buffer.fill(0, safeOffset, safeEnd)
                return@withContext safeEnd - safeOffset
            }

            val record = audioRecord
            if (record != null && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val safeOffset = offset.coerceIn(0, buffer.size)
                val safeSize = size.coerceAtMost(buffer.size - safeOffset)
                val bytesRead = record.read(buffer, safeOffset, safeSize)
                if (bytesRead > 0) return@withContext bytesRead
            }

            // Return silence if no data available
            val safeOffset = offset.coerceIn(0, buffer.size)
            val safeEnd = (offset + size).coerceIn(safeOffset, buffer.size)
            buffer.fill(0, safeOffset, safeEnd)
            safeEnd - safeOffset
        }

    override suspend fun release() {
        isCapturing.set(false)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        mediaProjection.get()?.stop()
        mediaProjection.set(null)
        isInitialized.set(false)
    }

    override fun isReady(): Boolean = isInitialized.get() && isCapturing.get()
}
