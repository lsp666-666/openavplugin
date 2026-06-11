package com.openavplugin.hook

import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.camera2.*
import android.os.Handler
import android.os.Looper
import android.view.Surface
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.openavplugin.provider.VideoSource
import com.openavplugin.provider.VideoSource.Frame
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class CameraHooker(private val lpparam: XC_LoadPackage.LoadPackageParam) {
    companion object {
        private const val TAG = "OpenAVPlugin-Camera"
    }

    private val videoSources = ConcurrentHashMap<String, VideoSource>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun hook() {
        hookCamera2()
        hookCamera1()
    }

    private fun hookCamera2() {
        try {
            // Hook CameraManager.openCamera
            val cameraManagerClass = XposedHelpers.findClass(
                "android.hardware.camera2.CameraManager",
                lpparam.classLoader
            )

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

                        // Replace with virtual camera
                        param.args[1] = createVirtualCameraCallback(cameraId, callback, handler)
                    }
                }
            )

            XposedBridge.log("$TAG: Camera2 hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook Camera2: ${e.message}")
        }
    }

    private fun hookCamera1() {
        try {
            // Hook Camera.open
            val cameraClass = XposedHelpers.findClass(
                "android.hardware.Camera",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                cameraClass,
                "open",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cameraId = param.args[0] as Int
                        XposedBridge.log("$TAG: Camera.open intercepted for camera $cameraId")

                        // Let it proceed normally for now
                        // Full implementation would return a proxy Camera object
                    }
                }
            )

            XposedBridge.log("$TAG: Camera1 hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook Camera1: ${e.message}")
        }
    }

    private fun createVirtualCameraCallback(
        cameraId: String,
        originalCallback: CameraDevice.StateCallback,
        handler: Handler
    ): CameraDevice.StateCallback {
        return object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                XposedBridge.log("$TAG: Virtual camera opened: $cameraId")
                originalCallback.onOpened(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                originalCallback.onDisconnected(camera)
            }

            override fun onError(camera: CameraDevice, error: Int) {
                originalCallback.onError(camera, error)
            }
        }
    }

    fun release() {
        scope.cancel()
        videoSources.values.forEach { runBlocking { it.release() } }
        videoSources.clear()
    }
}
