package com.meta.wearable.retail

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.retail.util.SpressoLogger
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class RetailApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            // Add any additional Spresso-specific options here
        }

        Wearables.initialize(this).onFailure { error, _ ->
            SpressoLogger.e("RetailApp", "Wearables SDK Initialization FAILED: ${error.toString()}")
        }
    }
}
