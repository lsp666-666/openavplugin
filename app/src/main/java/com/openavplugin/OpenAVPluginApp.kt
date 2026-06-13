package com.openavplugin

import android.app.Application
import com.openavplugin.data.ConfigManager
import com.openavplugin.util.Logger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltAndroidApp
class OpenAVPluginApp : Application() {

    @Inject
    lateinit var configManager: ConfigManager

    override fun onCreate() {
        super.onCreate()
        // Initialize logger with saved log level
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Logger.setLevel(configManager.logLevel.first())
                Logger.i("App", "Logger initialized — level=${Logger.getLevel()}")
            } catch (_: Exception) {
                Logger.setLevel("INFO")
            }
        }
    }
}
