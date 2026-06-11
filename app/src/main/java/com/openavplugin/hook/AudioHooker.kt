package com.openavplugin.hook

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.openavplugin.provider.AudioSource
import com.openavplugin.provider.audio.SilenceAudioSource
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class AudioHooker(private val lpparam: XC_LoadPackage.LoadPackageParam) {
    companion object {
        private const val TAG = "OpenAVPlugin-Audio"
    }

    private val audioSources = ConcurrentHashMap<String, AudioSource>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun hook() {
        hookAudioRecord()
        hookMediaRecorder()
    }

    private fun hookAudioRecord() {
        try {
            val audioRecordClass = XposedHelpers.findClass(
                "android.media.AudioRecord",
                lpparam.classLoader
            )

            // Hook startRecording
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "startRecording",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: AudioRecord.startRecording intercepted")
                        // Mark this instance as using virtual source
                        val instance = param.thisObject
                        XposedHelpers.setAdditionalInstanceField(instance, "useVirtualSource", true)
                    }
                }
            )

            // Hook read (short array version)
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "read",
                ShortArray::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val instance = param.thisObject
                        val useVirtual = XposedHelpers.getAdditionalInstanceField(instance, "useVirtualSource")
                        if (useVirtual == true) {
                            val audioData = param.args[0] as ShortArray
                            val offset = param.args[1] as Int
                            val size = param.args[2] as Int

                            // Fill with silence (zeros)
                            audioData.fill(0, offset, offset + size)
                            param.result = size
                        }
                    }
                }
            )

            // Hook read (byte array version)
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "read",
                ByteArray::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val instance = param.thisObject
                        val useVirtual = XposedHelpers.getAdditionalInstanceField(instance, "useVirtualSource")
                        if (useVirtual == true) {
                            val audioData = param.args[0] as ByteArray
                            val offset = param.args[1] as Int
                            val size = param.args[2] as Int

                            // Fill with silence (zeros)
                            audioData.fill(0, offset, offset + size)
                            param.result = size
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: AudioRecord hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook AudioRecord: ${e.message}")
        }
    }

    private fun hookMediaRecorder() {
        try {
            val mediaRecorderClass = XposedHelpers.findClass(
                "android.media.MediaRecorder",
                lpparam.classLoader
            )

            // Hook start
            XposedHelpers.findAndHookMethod(
                mediaRecorderClass,
                "start",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: MediaRecorder.start intercepted")
                    }
                }
            )

            XposedBridge.log("$TAG: MediaRecorder hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook MediaRecorder: ${e.message}")
        }
    }

    fun getAudioSource(key: String): AudioSource {
        return audioSources.getOrPut(key) { SilenceAudioSource() }
    }

    fun release() {
        scope.cancel()
        audioSources.values.forEach { runBlocking { it.release() } }
        audioSources.clear()
    }
}
