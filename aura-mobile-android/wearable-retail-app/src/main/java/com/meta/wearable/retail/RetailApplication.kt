package com.meta.wearable.retail

import android.app.Application
import com.meta.wearable.dat.core.Wearables
import android.util.Log

class RetailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase initialization is handled automatically by the google-services plugin
        Wearables.initialize(this)
    }
}
