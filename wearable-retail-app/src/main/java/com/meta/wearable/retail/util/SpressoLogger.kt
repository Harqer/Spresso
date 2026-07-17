package com.meta.wearable.retail.util

import android.util.Log
import com.meta.wearable.retail.BuildConfig

/**
 * Enterprise Production Logger.
 * Automatically strips or silences non-critical logs in release builds
 * to protect user privacy and system performance.
 */
object SpressoLogger {
    private const val TAG = "SpressoRetail"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        // Warnings are logged in production but only via high-level descriptors
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Critical errors are always logged and should be sent to Sentry
        Log.e(tag, message, throwable)
    }
}
