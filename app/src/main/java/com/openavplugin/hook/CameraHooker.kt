package com.openavplugin.hook

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.openavplugin.provider.VideoSource
import com.openavplugin.provider.VideoConfig
import com.openavplugin.provider.VideoSource.Frame
import com.openavplugin.provider.video.LocalFileVideoSource
import com.openavplugin.provider.video.StreamVideoSource
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class CameraHooker(
    private val lpparam: XC_LoadPackage.LoadPackageParam,
    private val config: CameraHookConfig
) {
    data class CameraHookConfig(
        val sourceType: String = "local",
        val sourcePath: String? = null,
        val width: Int = 1280,
        val height: Int = 720,
        val fps: Int = 30
    )

    companion object {
        private const val TAG = "OpenAVPlugin-Camera"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var videoSource: VideoSource? = null
    private val virtualCameras = ConcurrentHashMap<String, CameraDevice>()
    private val captureSurfaces = ConcurrentHashMap<String, MutableList<Surface>>()
    private val frameJobs = ConcurrentHashMap<String, Job>()
    private val isFeeding = AtomicBoolean(false)

    fun hook() {
        initializeVideoSource()
        hookCamera2()
        hookCamera1()
    }

    private fun initializeVideoSource() {
        videoSource = when (config.sourceType) {
            "local", "LOCAL_VIDEO" -> {
                if (config.sourcePath != null) LocalFileVideoSource() else null
            }
            "stream", "NETWORK_STREAM" -> {
                if (config.sourcePath != null) {
                    // Try to get a context from the current process
                    val ctx = getApplicationContext()
                    if (ctx != null) StreamVideoSource(ctx) else null
                } else null
            }
            else -> null
        }

        if (videoSource != null) {
            scope.launch {
                try {
                    videoSource?.initialize(
                        VideoConfig(
                            width = config.width,
                            height = config.height,
                            fps = config.fps,
                            sourcePath = config.sourcePath
                        )
                    )
                    XposedBridge.log("$TAG: VideoSource initialized: ${config.sourcePath}")
                } catch (e: Exception) {
                    XposedBridge.log("$TAG: Failed to initialize VideoSource: ${e.message}")
                }
            }
        }
        isFeeding.set(true)
    }

    // ── Camera2 hooks ──────────────────────────────────────────

    private fun hookCamera2() {
        try {
            val cameraManagerClass = classOrNull("android.hardware.camera2.CameraManager")
                ?: return

            // Hook CameraManager.openCamera to intercept camera open
            XposedHelpers.findAndHookMethod(
                cameraManagerClass,
                "openCamera",
                String::class.java,
                CameraDevice.StateCallback::class.java,
                Handler::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cameraId = param.args[0] as String
                        val callback = param.args[1] as CameraDevice.StateCallback
                        val handler = param.args[2] as? Handler ?: Handler(Looper.getMainLooper())

                        XposedBridge.log("$TAG: openCamera intercepted for camera $cameraId")
                        param.args[1] = VirtualCameraCallback(cameraId, callback, handler)
                    }
                }
            )

            XposedBridge.log("$TAG: Camera2 hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook Camera2: ${e.message}")
        }
    }

    // ── Camera1 hooks (basic) ───────────────────────────────────

    private fun hookCamera1() {
        try {
            val cameraClass = classOrNull("android.hardware.Camera") ?: return

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "open",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val cameraId = param.args[0] as Int
                        XposedBridge.log("$TAG: Camera.open intercepted for camera $cameraId")
                        // Hook the Camera instance to inject preview frames
                        val camera = param.result ?: return
                        hookCamera1Preview(camera, cameraId.toString())
                    }
                }
            )

            XposedBridge.log("$TAG: Camera1 hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook Camera1: ${e.message}")
        }
    }

    private fun hookCamera1Preview(camera: Any, cameraId: String) {
        try {
            XposedHelpers.findAndHookMethod(
                camera.javaClass,
                "setPreviewCallback",
                Camera.PreviewCallback::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val originalCallback = param.args[0] as? Camera.PreviewCallback
                        param.args[0] = Camera.PreviewCallback { data, cam ->
                            // Inject virtual frame into preview data
                            if (isFeeding.get() && data != null) {
                                fillFrameData(data, cameraId)
                            }
                            originalCallback?.onPreviewFrame(data, cam)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook Camera1 preview: ${e.message}")
        }
    }

    // ── Virtual Camera Callback ─────────────────────────────────

    private inner class VirtualCameraCallback(
        private val cameraId: String,
        private val originalCallback: CameraDevice.StateCallback,
        private val handler: Handler
    ) : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            XposedBridge.log("$TAG: Virtual camera opened: $cameraId")
            virtualCameras[cameraId] = camera
            // Hook createCaptureSession on this camera device
            hookCreateCaptureSession(camera, cameraId)
            // Also hook createCaptureRequest for template injection
            hookCreateCaptureRequest(camera, cameraId)
            originalCallback.onOpened(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            XposedBridge.log("$TAG: Camera disconnected: $cameraId")
            stopFrameFeeding(cameraId)
            virtualCameras.remove(cameraId)
            originalCallback.onDisconnected(camera)
        }

        override fun onError(camera: CameraDevice, error: Int) {
            XposedBridge.log("$TAG: Camera error: $cameraId, error=$error")
            stopFrameFeeding(cameraId)
            virtualCameras.remove(cameraId)
            originalCallback.onError(camera, error)
        }
    }

    // ── Capture Session Hook ────────────────────────────────────

    private fun hookCreateCaptureSession(camera: CameraDevice, cameraId: String) {
        try {
            XposedHelpers.findAndHookMethod(
                camera.javaClass,
                "createCaptureSession",
                List::class.java,
                CameraCaptureSession.StateCallback::class.java,
                Handler::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val surfaces = param.args[0] as? List<Surface> ?: return
                        XposedBridge.log("$TAG: createCaptureSession with ${surfaces.size} surfaces")

                        // Store surfaces for frame feeding
                        captureSurfaces[cameraId] = surfaces.toMutableList()

                        // Start feeding frames if a video source is configured
                        if (videoSource?.isReady() == true) {
                            startFrameFeeding(cameraId, surfaces)
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook createCaptureSession: ${e.message}")
        }
    }

    private fun hookCreateCaptureRequest(camera: CameraDevice, cameraId: String) {
        try {
            XposedHelpers.findAndHookMethod(
                camera.javaClass,
                "createCaptureRequest",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Add target surfaces to capture requests
                        try {
                            val surfaces = captureSurfaces[cameraId] ?: return
                            val builder = param.result ?: return
                            for (surface in surfaces) {
                                if (surface.isValid) {
                                    XposedHelpers.callMethod(builder, "addTarget", surface)
                                }
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("$TAG: Error adding targets: ${e.message}")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook createCaptureRequest: ${e.message}")
        }
    }

    // ── Frame Feeding Pipeline ──────────────────────────────────

    private fun startFrameFeeding(cameraId: String, surfaces: List<Surface>) {
        val job = scope.launch {
            val source = videoSource ?: return@launch
            val frameInterval = 1000L / config.fps // ms between frames

            while (isActive && isFeeding.get()) {
                val frame = withContext(Dispatchers.IO) {
                    source.getNextFrame()
                }
                if (frame == null) {
                    delay(10)
                    continue
                }

                // Write frame to each valid surface
                for (surface in surfaces) {
                    if (!surface.isValid) continue
                    try {
                        writeFrameToSurface(surface, frame)
                    } catch (e: Exception) {
                        XposedBridge.log("$TAG: Error writing frame: ${e.message}")
                    }
                }

                delay(frameInterval)
            }
        }
        frameJobs[cameraId]?.cancel()
        frameJobs[cameraId] = job
    }

    private fun stopFrameFeeding(cameraId: String) {
        frameJobs.remove(cameraId)?.cancel()
        captureSurfaces.remove(cameraId)
    }

    private fun writeFrameToSurface(surface: Surface, frame: Frame) {
        // Use ImageReader pattern: lock canvas, write frame, unlock
        try {
            val canvas = surface.lockCanvas(null)
            if (canvas != null) {
                val yuv = ByteBuffer.wrap(frame.data)
                // In practice, convert YUV to RGB bitmap and draw on canvas
                // For now, write raw frame data via the native surface API
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            // Surface may not support Canvas (e.g., ImageReader surfaces)
            // Fall back to raw image injection
            tryInjectRawImage(surface, frame)
        }
    }

    private fun tryInjectRawImage(surface: Surface, frame: Frame) {
        try {
            // Use native ImageWriter or direct buffer copy to surface
            // This requires more platform-specific code
            // For now, log that we'd inject here
            XposedBridge.log("$TAG: Frame ready (${frame.width}x${frame.height}, ${frame.data.size}B)")
        } catch (_: Exception) {}
    }

    // ── Camera1 fill helpers ────────────────────────────────────

    private fun fillFrameData(data: ByteArray, cameraId: String) {
        scope.launch {
            val frame = videoSource?.getNextFrame() ?: return@launch
            val copyLen = minOf(frame.data.size, data.size)
            System.arraycopy(frame.data, 0, data, 0, copyLen)
            // Fill remaining with black (NV21: Y=16=black for limited range, UV=128)
            for (i in copyLen until data.size) {
                data[i] = if (i < data.size * 2 / 3) 16.toByte() else 128.toByte()
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun classOrNull(className: String): Class<*>? {
        return try {
            XposedHelpers.findClass(className, lpparam.classLoader)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Get an Android Context from within the Xposed target process.
     * Tries multiple approaches because different Android versions expose
     * the context differently to Xposed modules.
     */
    private fun getApplicationContext(): android.content.Context? {
        return try {
            // Approach 1: ActivityThread.currentActivityThread().application
            val atCls = Class.forName("android.app.ActivityThread")
            val at = atCls.getMethod("currentActivityThread").invoke(null)
            val app = atCls.getMethod("getApplication").invoke(at)
            app as? android.content.Context
        } catch (_: Throwable) {
            try {
                // Approach 2: ActivityThread.systemContext
                val atCls = Class.forName("android.app.ActivityThread")
                val at = atCls.getMethod("currentActivityThread").invoke(null)
                val context = atCls.getMethod("getSystemContext").invoke(at)
                context as? android.content.Context
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun release() {
        isFeeding.set(false)
        frameJobs.values.forEach { it.cancel() }
        scope.cancel()
        scope.launch { videoSource?.release() }
        virtualCameras.clear()
        captureSurfaces.clear()
    }
}
