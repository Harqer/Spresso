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
            options.dsn = "https://REDACTED_SENTRY_KEY@o***REDACTED_SENTRY_ORG_ID***.ingest.us.sentry.io/***REDACTED_SENTRY_ID***"
            // Add any additional Spresso-specific options here
        }

        Wearables.initialize(this)
    }
}
