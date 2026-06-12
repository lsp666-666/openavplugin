package com.openavplugin.root

object NativeInjector {
    init {
        System.loadLibrary("openavplugin_cam")
        System.loadLibrary("openavplugin_audio")
    }

    external fun initCameraHook()
    external fun initAudioHook()
    external fun isCameraHookActive(): Boolean
    external fun isAudioHookActive(): Boolean

    fun initialize() {
        initCameraHook()
        initAudioHook()
    }

    val isActive: Boolean
        get() = isCameraHookActive() || isAudioHookActive()
}
