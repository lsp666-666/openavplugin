package com.openavplugin.provider.video

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource
import com.openavplugin.provider.VideoConfig
import com.openavplugin.provider.VideoSource
import com.openavplugin.provider.VideoSource.Frame
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class StreamVideoSource(private val appContext: android.content.Context) : VideoSource {
    private var player: ExoPlayer? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var isInitialized = false
    private var videoWidth = 1280
    private var videoHeight = 720
    private val frameQueue = LinkedBlockingQueue<Frame>(30) // max 30 frames buffer

    override suspend fun initialize(config: VideoConfig) = withContext(Dispatchers.IO) {
        val url = config.streamUrl ?: throw IllegalArgumentException("Stream URL required")

        videoWidth = config.width
        videoHeight = config.height

        // Create ImageReader that ExoPlayer will render to
        imageReader = ImageReader.newInstance(
            videoWidth, videoHeight,
            PixelFormat.RGBA_8888, 2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val planes = image.planes
                    if (planes.isNotEmpty()) {
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - videoWidth * pixelStride

                        val data = ByteArray(buffer.remaining())
                        buffer.get(data)

                        if (frameQueue.remainingCapacity() == 0) {
                            frameQueue.poll() // drop oldest frame
                        }
                        frameQueue.offer(
                            Frame(
                                data = convertRGBAToNV21(data, videoWidth, videoHeight),
                                width = videoWidth,
                                height = videoHeight,
                                format = ImageFormat.NV21
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore frame conversion errors
                } finally {
                    image.close()
                }
            }
        }, handler)

        // Set up ExoPlayer with the ImageReader surface
        val surface = imageReader!!.surface
        player = ExoPlayer.Builder(appContext)
            .build()
            .apply {
                setVideoSurface(surface)
                val httpFactory = com.google.android.exoplayer2.upstream.DefaultHttpDataSource.Factory()
                val mediaSource = when {
                    url.startsWith("rtsp://") -> RtspMediaSource.Factory()
                        .createMediaSource(MediaItem.fromUri(url))
                    else -> {
                        val progressiveFactory = com.google.android.exoplayer2.source.ProgressiveMediaSource.Factory(httpFactory)
                        progressiveFactory.createMediaSource(MediaItem.fromUri(url))
                    }
                }
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }

        // Start a background thread for ImageReader
        handlerThread = HandlerThread("StreamVideoDecoder").apply { start() }
        handler = Handler(handlerThread!!.looper)

        isInitialized = true
    }

    override suspend fun getNextFrame(): Frame? = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext null
        // Poll with timeout — return null if no frame available
        frameQueue.poll(33, TimeUnit.MILLISECONDS) // ~30fps
    }

    override suspend fun release() {
        isInitialized = false
        player?.stop()
        player?.release()
        player = null
        imageReader?.close()
        imageReader = null
        handlerThread?.quitSafely()
        handlerThread = null
        frameQueue.clear()
    }

    override fun isReady(): Boolean = isInitialized

    // ── Color conversion: RGBA_8888 → NV21 (YUV 4:2:0) ──────────

    private fun convertRGBAToNV21(rgba: ByteArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val yuv = ByteArray(frameSize + frameSize / 2)
        var yIndex = 0
        var uvIndex = frameSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixelIndex = (j * width + i) * 4
                val r = rgba[pixelIndex].toInt() and 0xFF
                val g = rgba[pixelIndex + 1].toInt() and 0xFF
                val b = rgba[pixelIndex + 2].toInt() and 0xFF

                // RGB to YUV
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                // UV: every 2x2 block
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
