package com.meta.wearable.retail

import android.app.Application
import com.meta.wearable.dat.core.Wearables
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RetailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Wearables.initialize(this)
    }
}
