package com.openavplugin.provider.video

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.openavplugin.provider.VideoConfig
import com.openavplugin.provider.VideoSource
import com.openavplugin.provider.VideoSource.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureVideoSource : VideoSource {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var isInitialized = AtomicBoolean(false)
    private var videoWidth = 1280
    private var videoHeight = 720
    private val frameQueue = LinkedBlockingQueue<Frame>(30)

    override suspend fun initialize(config: VideoConfig) = withContext(Dispatchers.IO) {
        videoWidth = config.width
        videoHeight = config.height

        // Create ImageReader for frame capture
        imageReader = ImageReader.newInstance(
            videoWidth, videoHeight,
            PixelFormat.RGBA_8888, 2
        )

        handlerThread = HandlerThread("ScreenCapture").apply { start() }
        handler = Handler(handlerThread!!.looper)

        imageReader?.setOnImageAvailableListener({ reader ->
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val planes = image.planes
                    if (planes.isNotEmpty()) {
                        val buffer = planes[0].buffer
                        val remaining = buffer.remaining()
                        if (remaining > 0) {
                            val rgbaData = ByteArray(remaining)
                            buffer.get(rgbaData)

                            if (frameQueue.remainingCapacity() == 0) {
                                frameQueue.poll()
                            }
                            frameQueue.offer(
                                Frame(
                                    data = convertRGBAToNV21(rgbaData, videoWidth, videoHeight),
                                    width = videoWidth,
                                    height = videoHeight,
                                    format = ImageFormat.NV21
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Dropped frame
                } finally {
                    image.close()
                }
            }
        }, handler)

        isInitialized.set(true)
    }

    override suspend fun getNextFrame(): Frame? = withContext(Dispatchers.IO) {
        if (!isInitialized.get() || mediaProjection == null) return@withContext null
        frameQueue.poll(33, TimeUnit.MILLISECONDS)
    }

    /**
     * Attach a MediaProjection and start capturing the screen.
     * Call this from CaptureService after obtaining the projection.
     */
    fun startCapture(projection: MediaProjection) {
        mediaProjection = projection
        val reader = imageReader ?: return

        // Create VirtualDisplay to capture the screen
        virtualDisplay = projection.createVirtualDisplay(
            "OpenAVPlugin-ScreenCapture",
            videoWidth, videoHeight,
            160, // density (mdpi baseline)
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null, // VirtualDisplay.Callback
            handler
        )
    }

    fun setMediaProjection(projection: MediaProjection) {
        startCapture(projection)
    }

    override suspend fun release() {
        isInitialized.set(false)
        frameQueue.clear()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        handlerThread?.quitSafely()
        handlerThread = null
    }

    override fun isReady(): Boolean = isInitialized.get() && mediaProjection != null

    // ── Color conversion ─── (reuse from StreamVideoSource) ──────

    private fun convertRGBAToNV21(rgba: ByteArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val yuv = ByteArray(frameSize + frameSize / 2)
        var yIndex = 0
        var uvIndex = frameSize

        for (j in 0 until height) {
            for (i in 0 until width) {
                val idx = (j * width + i) * 4
                val r = rgba[idx].toInt() and 0xFF
                val g = rgba[idx + 1].toInt() and 0xFF
                val b = rgba[idx + 2].toInt() and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

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
