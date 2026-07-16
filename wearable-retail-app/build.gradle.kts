import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.meta.wearable.retail"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.meta.wearable.retail"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        val backendUrl: String = System.getenv("SPRESSO_BACKEND_URL") 
            ?: properties.getProperty("SPRESSO_BACKEND_URL")
            ?: "https://aura-edge-service.quantumcoin.workers.dev"
        val googleId: String = System.getenv("GOOGLE_WEB_CLIENT_ID") 
            ?: properties.getProperty("GOOGLE_WEB_CLIENT_ID")
            ?: ""
        val internalSecret: String = System.getenv("SPRESSO_INTERNAL_SECRET") 
            ?: properties.getProperty("SPRESSO_INTERNAL_SECRET")
            ?: ""
        val spressoDomain: String = System.getenv("SPRESSO_DOMAIN") 
            ?: properties.getProperty("SPRESSO_DOMAIN")
            ?: "spresso.wearables.com"

        buildConfigField("String", "SPRESSO_BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleId\"")
        buildConfigField("String", "SPRESSO_DOMAIN", "\"$spressoDomain\"")
        buildConfigField("String", "SPRESSO_INTERNAL_SECRET", "\"$internalSecret\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    configurations.all {
        resolutionStrategy {
            // Industrial Hardening: Force explicit cache timeouts for dynamic versions
            cacheDynamicVersionsFor(600, "seconds")
            cacheChangingModulesFor(600, "seconds")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.biometric.ktx)
    implementation(libs.material)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-vertexai:16.2.0")

    implementation(libs.androidx.datastore.preferences)

    // Meta Wearables DAT SDK
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.display)
    implementation(libs.mwdat.camera)

    // Wearable DataClient for Wear OS sync
    implementation(libs.play.services.wearable)

    implementation(libs.glimmer)
    implementation(libs.glimmer.fonts)

    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Sentry SDK
    implementation("io.sentry:sentry-android:8.3.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
