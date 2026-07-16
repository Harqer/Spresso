package com.meta.wearable.retail

import android.app.Application
import com.meta.wearable.dat.core.Wearables
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class RetailApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            // Add any additional Spresso-specific options here
        }

        Wearables.initialize(this).onFailure { error, _ ->
            android.util.Log.e("RetailApp", "Wearables SDK Initialization FAILED: ${error.toString()}")
        }
    }
}
