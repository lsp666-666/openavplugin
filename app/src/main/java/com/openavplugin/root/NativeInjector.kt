package com.openavplugin.root

object NativeInjector {
    init {
        System.loadLibrary("openavplugin_cam")
        System.loadLibrary("openavplugin_audio")
    }

    external fun initCameraHook()
    external fun initAudioHook()

    fun initialize() {
        initCameraHook()
        initAudioHook()
    }
}
