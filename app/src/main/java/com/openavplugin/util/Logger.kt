package com.openavplugin.util

import android.util.Log

/**
 * Lightweight logger wrapping android.util.Log with level-based filtering.
 *
 * Usage:
 *   Logger.i("MyTag", "message")
 *   Logger.d("MyTag", "debug detail")
 *   Logger.e("MyTag", "error", throwable)
 *
 * Set the minimum log level via [setLevel]:
 *   Logger.setLevel("DEBUG")   // show all
 *   Logger.setLevel("INFO")    // show info + warn + error (default)
 *   Logger.setLevel("ERROR")   // show errors only
 */
object Logger {

    enum class Level(val priority: Int) {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR)
    }

    private var minLevel: Level = Level.INFO

    fun setLevel(level: String) {
        minLevel = when (level.uppercase()) {
            "DEBUG" -> Level.DEBUG
            "INFO" -> Level.INFO
            "WARN" -> Level.WARN
            "ERROR" -> Level.ERROR
            "VERBOSE" -> Level.VERBOSE
            else -> Level.INFO
        }
    }

    fun getLevel(): String = minLevel.name

    fun d(tag: String, message: String) {
        if (minLevel.priority <= Log.DEBUG) {
            Log.d(tag, message)
        }
        LogServer.append("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        if (minLevel.priority <= Log.INFO) {
            Log.i(tag, message)
        }
        LogServer.append("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        if (minLevel.priority <= Log.WARN) {
            Log.w(tag, message)
        }
        LogServer.append("WARN", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (minLevel.priority <= Log.ERROR) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
        LogServer.append("ERROR", tag, message)
    }
}
