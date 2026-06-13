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
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class AudioHooker(
    private val lpparam: XC_LoadPackage.LoadPackageParam,
    private val config: AudioHookConfig
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
        initializeAudioSource()
        hookAudioRecord()
        hookMediaRecorder()
    }

    private fun initializeAudioSource() {
        audioSource = when (config.sourceType) {
            "silence", "SILENCE" -> SilenceAudioSource()
            else -> when {
                config.sourcePath != null -> LocalFileAudioSource()
                else -> SilenceAudioSource()
            }
        }

        scope.launch {
            try {
                audioSource?.initialize(
                    AudioConfig(
                        sampleRate = config.sampleRate,
                        channels = config.channels,
                        sourcePath = config.sourcePath
                    )
                )
                XposedBridge.log("$TAG: AudioSource initialized (type=${config.sourceType})")
            } catch (e: Exception) {
                XposedBridge.log("$TAG: Failed to initialize AudioSource: ${e.message}")
            }
        }
    }

    private fun hookAudioRecord() {
        try {
            val audioRecordClass = XposedHelpers.findClass(
                "android.media.AudioRecord",
                lpparam.classLoader
            )

            // Hook startRecording — mark instance as virtual
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "startRecording",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("$TAG: AudioRecord.startRecording intercepted")
                        val instance = param.thisObject
                        XposedHelpers.setAdditionalInstanceField(instance, FIELD_USE_VIRTUAL, true)

                        // Assign an audio source if configured
                        val source = audioSource
                        if (source != null) {
                            XposedHelpers.setAdditionalInstanceField(instance, FIELD_AUDIO_SOURCE, source)
                            audioSources[instance] = source
                        }
                    }
                }
            )

            // Hook read(short[]) — inject virtual audio
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "read",
                ShortArray::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isVirtualInstance(param.thisObject)) return

                        val audioData = param.args[0] as ShortArray
                        val offset = param.args[1] as Int
                        val size = param.args[2] as Int
                        val source = getSourceForInstance(param.thisObject)

                        // Read actual audio data from source
                        val bytesRead = readFromSource(source, audioData, offset, size)
                        if (bytesRead > 0) {
                            param.result = bytesRead
                        }
                    }
                }
            )

            // Hook read(byte[]) — inject virtual audio
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "read",
                ByteArray::class.java,
                Int::class.java,
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isVirtualInstance(param.thisObject)) return

                        val audioData = param.args[0] as ByteArray
                        val offset = param.args[1] as Int
                        val size = param.args[2] as Int
                        val source = getSourceForInstance(param.thisObject)

                        val bytesRead = readFromSourceBytes(source, audioData, offset, size)
                        if (bytesRead > 0) {
                            param.result = bytesRead
                        }
                    }
                }
            )

            // Hook read(byte[], int, int) for direct buffer access
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "read",
                java.nio.ByteBuffer::class.java,
                Int::class.java,
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

            // Hook stop + release to clean up
            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "stop",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isVirtualInstance(param.thisObject)) {
                            XposedBridge.log("$TAG: AudioRecord.stop — cleaning up virtual source")
                            cleanupInstance(param.thisObject)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                audioRecordClass,
                "release",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isVirtualInstance(param.thisObject)) {
                            cleanupInstance(param.thisObject)
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
