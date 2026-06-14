package com.openavplugin.hook

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.openavplugin.provider.AudioSource
import com.openavplugin.provider.AudioConfig
import com.openavplugin.provider.audio.SilenceAudioSource
import com.openavplugin.provider.audio.LocalFileAudioSource
import org.json.JSONObject
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class AudioHooker(
    private val lpparam: XC_LoadPackage.LoadPackageParam,
    private val packageName: String
) {
    data class AudioHookConfig(
        val sourceType: String = "silence",
        val sourcePath: String? = null,
        val sampleRate: Int = 44100,
        val channels: Int = 1
    )

    companion object {
        private const val TAG = "OpenAVPlugin-Audio"
        private const val FIELD_USE_VIRTUAL = "openavplugin_useVirtual"
        private const val FIELD_AUDIO_SOURCE = "openavplugin_audioSource"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioSource: AudioSource? = null
    private val audioSources = ConcurrentHashMap<Any, AudioSource>()

    fun hook() {
        XposedBridge.log("$TAG: Installing hooks for process=${lpparam.processName}")
        hookAudioRecord()
        hookMediaRecorder()
    }

    private fun hookAudioRecord() {
        try {
            // Use Class.forName to ensure we hook the system class, not app-specific
            val audioRecordClass = Class.forName("android.media.AudioRecord")

            // Diagnostic: log when AudioRecord instance is created
            try {
                XposedBridge.hookAllConstructors(audioRecordClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: AudioRecord created in ${lpparam.processName}")
                    }
                })
            } catch (_: Throwable) { }

            // Hook startRecording — mark instance as virtual
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "startRecording",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: AudioRecord.startRecording intercepted")
                        markVirtual(param.thisObject)
                    }
                }
            )

            // Hook startRecording(MediaSyncEvent) — API 24+
            try {
                val syncEventClass = XposedHelpers.findClass(
                    "android.media.MediaSyncEvent", lpparam.classLoader
                )
                XposedHelpers.findAndHookMethod(
                    audioRecordClass, "startRecording", syncEventClass,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("$TAG: AudioRecord.startRecording(sync) intercepted")
                            markVirtual(param.thisObject)
                        }
                    }
                )
            } catch (_: Throwable) { }

            // Hook read(short[]) — inject virtual audio
            hookReadShort(audioRecordClass)
            // Hook read(byte[]) — inject virtual audio
            hookReadByte(audioRecordClass)
            // Hook read(ByteBuffer) — direct buffer
            hookReadByteBuffer(audioRecordClass)
            // Hook read(float[]) — API 23+
            try { hookReadFloat(audioRecordClass) } catch (_: Throwable) {}

            // Hook setRecordPositionUpdateListener — callback mode
            try { hookCallbackListener(audioRecordClass) } catch (_: Throwable) {}

            // Hook stop + release to clean up
            hookStopRelease(audioRecordClass)

            XposedBridge.log("$TAG: AudioRecord hooks installed")

            // Hook AudioRecord.Builder.build() — ensure builder-created instances are caught
            try {
                val builderClass = XposedHelpers.findClass(
                    "android.media.AudioRecord\$Builder", lpparam.classLoader
                )
                XposedHelpers.findAndHookMethod(
                    builderClass, "build",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("$TAG: AudioRecord.Builder.build() — pre-marking virtual")
                            // Don't mark here — wait for startRecording
                        }
                    }
                )
            } catch (_: Throwable) { }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook AudioRecord: ${e.message}")
        }
    }

    private fun markVirtual(instance: Any) {
        // Reload latest config on each recording start
        val freshConfig = loadLatestConfig()
        XposedBridge.log("$TAG: startRecording — sourceType=${freshConfig.sourceType}")
        
        // Switch audio source
        try { kotlinx.coroutines.runBlocking { audioSource?.release() } } catch (_: Exception) { }
        audioSource = createSource(freshConfig)
        kotlinx.coroutines.runBlocking {
            try {
                audioSource?.initialize(AudioConfig(
                    sampleRate = freshConfig.sampleRate,
                    channels = freshConfig.channels,
                    sourcePath = freshConfig.sourcePath
                ))
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Source init failed: ${e.message}")
            }
        }
        
        XposedHelpers.setAdditionalInstanceField(instance, FIELD_USE_VIRTUAL, true)
        if (audioSource != null) {
            XposedHelpers.setAdditionalInstanceField(instance, FIELD_AUDIO_SOURCE, audioSource)
            audioSources[instance] = audioSource!!
        }
    }

    private fun loadLatestConfig(): AudioHookConfig {
        return try {
            val path = java.io.File("/data/local/tmp/openavplugin_rules.json")
            if (!path.exists()) return AudioHookConfig()
            val json = org.json.JSONObject(path.readText())
            val appConfig = json.optJSONObject(packageName)
            if (appConfig != null) {
                AudioHookConfig(
                    sourceType = appConfig.optString("micSourceType", "silence"),
                    sourcePath = appConfig.optString("micSourcePath", null).ifEmpty { null },
                    sampleRate = appConfig.optInt("micSampleRate", 44100),
                    channels = appConfig.optInt("micChannels", 1)
                )
            } else AudioHookConfig()
        } catch (_: Exception) { AudioHookConfig() }
    }

    private fun createSource(config: AudioHookConfig): AudioSource? {
        return when (config.sourceType) {
            "silence", "SILENCE" -> SilenceAudioSource()
            else -> {
                if (config.sourcePath != null) LocalFileAudioSource() else SilenceAudioSource()
            }
        }
    }

    private fun hookReadShort(audioRecordClass: Class<*>) {
        XposedHelpers.findAndHookMethod(
            audioRecordClass, "read", ShortArray::class.java, Int::class.java, Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isVirtualInstance(param.thisObject)) return
                    val audioData = param.args[0] as ShortArray
                    val offset = param.args[1] as Int
                    val size = param.args[2] as Int
                    val source = getSourceForInstance(param.thisObject)
                    val bytesRead = readFromSource(source, audioData, offset, size)
                    if (bytesRead > 0) param.result = bytesRead
                }
            }
        )
    }

    private fun hookReadByte(audioRecordClass: Class<*>) {
        XposedHelpers.findAndHookMethod(
            audioRecordClass, "read", ByteArray::class.java, Int::class.java, Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isVirtualInstance(param.thisObject)) return
                    val audioData = param.args[0] as ByteArray
                    val offset = param.args[1] as Int
                    val size = param.args[2] as Int
                    val source = getSourceForInstance(param.thisObject)
                    val bytesRead = readFromSourceBytes(source, audioData, offset, size)
                    if (bytesRead > 0) param.result = bytesRead
                }
            }
        )
    }

    private fun hookReadFloat(audioRecordClass: Class<*>) {
        XposedHelpers.findAndHookMethod(
            audioRecordClass, "read", FloatArray::class.java, Int::class.java, Int::class.java, Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isVirtualInstance(param.thisObject)) return
                    val audioData = param.args[0] as FloatArray
                    val offset = param.args[1] as Int
                    val size = param.args[2] as Int
                    val source = getSourceForInstance(param.thisObject)
                    // Fill with zeros (silence) for float samples
                    val safeEnd = (offset + size).coerceAtMost(audioData.size)
                    audioData.fill(0f, offset, safeEnd)
                    param.result = size
                }
            }
        )
    }

    private fun hookReadByteBuffer(audioRecordClass: Class<*>) {
        XposedHelpers.findAndHookMethod(
            audioRecordClass, "read", java.nio.ByteBuffer::class.java, Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isVirtualInstance(param.thisObject)) return
                    val buffer = param.args[0] as java.nio.ByteBuffer
                    val size = param.args[1] as Int
                    val source = getSourceForInstance(param.thisObject)
                    val tempBuffer = ByteArray(size)
                    val bytesRead = readFromSourceBytes(source, tempBuffer, 0, size)
                    if (bytesRead > 0) {
                        buffer.put(tempBuffer, 0, bytesRead)
                        param.result = bytesRead
                    }
                }
            }
        )
    }

    private fun hookCallbackListener(audioRecordClass: Class<*>) {
        XposedHelpers.findAndHookMethod(
            audioRecordClass, "setRecordPositionUpdateListener",
            android.media.AudioRecord.OnRecordPositionUpdateListener::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isVirtualInstance(param.thisObject)) return
                    val originalListener = param.args[0] as? android.media.AudioRecord.OnRecordPositionUpdateListener
                    if (originalListener != null) {
                        XposedBridge.log("$TAG: Replacing record position listener with virtual")
                        param.args[0] = VirtualRecordListener(originalListener, param.thisObject)
                    }
                }
            }
        )
        // Also hook the overload with Handler
        XposedHelpers.findAndHookMethod(
            audioRecordClass, "setRecordPositionUpdateListener",
            android.media.AudioRecord.OnRecordPositionUpdateListener::class.java,
            android.os.Handler::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isVirtualInstance(param.thisObject)) return
                    val originalListener = param.args[0] as? android.media.AudioRecord.OnRecordPositionUpdateListener
                    if (originalListener != null) {
                        param.args[0] = VirtualRecordListener(originalListener, param.thisObject)
                    }
                }
            }
        )
    }

    private inner class VirtualRecordListener(
        private val original: android.media.AudioRecord.OnRecordPositionUpdateListener,
        private val recordInstance: Any
    ) : android.media.AudioRecord.OnRecordPositionUpdateListener {
        override fun onMarkerReached(recorder: android.media.AudioRecord?) {
            // Feed silence before calling original
            injectVirtualData(recordInstance)
            original.onMarkerReached(recorder)
        }
        override fun onPeriodicNotification(recorder: android.media.AudioRecord?) {
            injectVirtualData(recordInstance)
            original.onPeriodicNotification(recorder)
        }
        private fun injectVirtualData(instance: Any) {
            try {
                val buffer = ShortArray(1024)
                val source = getSourceForInstance(instance)
                readFromSource(source, buffer, 0, buffer.size)
            } catch (_: Throwable) {}
        }
    }

    private fun hookStopRelease(audioRecordClass: Class<*>) {
        XposedHelpers.findAndHookMethod(audioRecordClass, "stop",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isVirtualInstance(param.thisObject)) {
                        XposedBridge.log("$TAG: AudioRecord.stop — cleaning up virtual source")
                        cleanupInstance(param.thisObject)
                    }
                }
            }
        )
        XposedHelpers.findAndHookMethod(audioRecordClass, "release",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isVirtualInstance(param.thisObject)) {
                        cleanupInstance(param.thisObject)
                    }
                }
            }
        )
    }

    private fun hookMediaRecorder() {
        try {
            val mediaRecorderClass = XposedHelpers.findClass(
                "android.media.MediaRecorder",
                lpparam.classLoader
            )

            // Hook start — mark as virtual and inject audio source
            XposedHelpers.findAndHookMethod(
                mediaRecorderClass,
                "start",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: MediaRecorder.start intercepted")
                        val instance = param.thisObject
                        val source = audioSource
                        if (source != null) {
                            XposedHelpers.setAdditionalInstanceField(instance, FIELD_USE_VIRTUAL, true)
                            XposedHelpers.setAdditionalInstanceField(instance, FIELD_AUDIO_SOURCE, source)
                            audioSources[instance] = source
                        }
                    }
                }
            )

            // Hook stop — cleanup
            XposedHelpers.findAndHookMethod(
                mediaRecorderClass,
                "stop",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isVirtualInstance(param.thisObject)) {
                            cleanupInstance(param.thisObject)
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: MediaRecorder hooks installed")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Failed to hook MediaRecorder: ${e.message}")
        }
    }

    // ── Source reading helpers ──────────────────────────────────

    private fun readFromSource(
        source: AudioSource?,
        buffer: ShortArray,
        offset: Int,
        size: Int
    ): Int {
        if (source == null || !source.isReady()) return 0

        // Convert short size to byte size, read from source, then convert back
        val byteBuffer = ByteArray(size * 2) // 2 bytes per short (16-bit PCM)
        return runBlocking {
            val bytesRead = source.read(byteBuffer, 0, minOf(byteBuffer.size, size * 2))
            if (bytesRead > 0) {
                val shortsRead = bytesRead / 2
                val safeOffset = offset.coerceIn(0, buffer.size)
                val safeEnd = (safeOffset + shortsRead).coerceAtMost(buffer.size)
                // Convert bytes to shorts (little-endian 16-bit PCM)
                for (i in safeOffset until safeEnd) {
                    val byteIdx = (i - safeOffset) * 2
                    if (byteIdx + 1 < byteBuffer.size) {
                        buffer[i] = ((byteBuffer[byteIdx].toInt() and 0xFF) or
                                     ((byteBuffer[byteIdx + 1].toInt() and 0xFF) shl 8)).toShort()
                    }
                }
                safeEnd - safeOffset
            } else {
                0
            }
        }
    }

    private fun readFromSourceBytes(
        source: AudioSource?,
        buffer: ByteArray,
        offset: Int,
        size: Int
    ): Int {
        if (source == null || !source.isReady()) return 0

        val safeOffset = offset.coerceIn(0, buffer.size)
        val safeSize = size.coerceAtMost(buffer.size - safeOffset)

        return runBlocking {
            source.read(buffer, safeOffset, safeSize)
        }
    }

    // ── Instance helpers ────────────────────────────────────────

    private fun isVirtualInstance(instance: Any): Boolean {
        return try {
            XposedHelpers.getAdditionalInstanceField(instance, FIELD_USE_VIRTUAL) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getSourceForInstance(instance: Any): AudioSource? {
        return try {
            XposedHelpers.getAdditionalInstanceField(instance, FIELD_AUDIO_SOURCE) as? AudioSource
        } catch (_: Throwable) {
            audioSource // fallback to global source
        }
    }

    private fun cleanupInstance(instance: Any) {
        try {
            XposedHelpers.setAdditionalInstanceField(instance, FIELD_USE_VIRTUAL, false)
        } catch (_: Throwable) {}
        try {
            XposedHelpers.removeAdditionalInstanceField(instance, FIELD_AUDIO_SOURCE)
        } catch (_: Throwable) {}
        audioSources.remove(instance)
    }

    fun release() {
        scope.cancel()
        audioSources.clear()
        scope.launch { audioSource?.release() }
    }
}
