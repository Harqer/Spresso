plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
        freeCompilerArgs.add("-Xskip-metadata-version-check")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

android {
    namespace = "com.meta.wearable.retail"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.meta.wearable.retail"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Industrial Security: Secrets injected via Infisical Environment
        val backendUrl: String = System.getenv("VAULTIER_BACKEND_URL") ?: project.findProperty("VAULTIER_BACKEND_URL") as String? ?: "https://aura-edge-service.quantumcoin.workers.dev"
        buildConfigField("String", "VAULTIER_BACKEND_URL", "\"$backendUrl\"")

        val googleId: String = System.getenv("GOOGLE_WEB_CLIENT_ID") ?: project.findProperty("GOOGLE_WEB_CLIENT_ID") as String? ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleId\"")

        val internalSecret: String = System.getenv("VAULTIER_INTERNAL_SECRET") ?: project.findProperty("VAULTIER_INTERNAL_SECRET") as String? ?: ""
        val vaultierDomain: String = System.getenv("VAULTIER_DOMAIN") ?: project.findProperty("VAULTIER_DOMAIN") as String? ?: "vaultier.wearables.com"
        buildConfigField("String", "VAULTIER_DOMAIN", "\"$vaultierDomain\"")
        buildConfigField("String", "VAULTIER_INTERNAL_SECRET", "\"$internalSecret\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.credentials:credentials:1.6.0-rc02")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0-rc02")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Industrial Identity: Firebase Auth (Replacing Clerk)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")

    implementation(libs.androidx.datastore.preferences)
    
    // Meta Wearables DAT SDK
    implementation("com.meta.wearable:mwdat-core:0.7.0")
    implementation("com.meta.wearable:mwdat-display:0.7.0")
    implementation("com.meta.wearable:mwdat-camera:0.7.0")
    debugImplementation("com.meta.wearable:mwdat-mockdevice:0.7.0")
    
    // Jetpack Compose Glimmer for glasses UI
    implementation("androidx.xr.glimmer:glimmer:1.0.0-alpha14")
    implementation("androidx.xr.glimmer:glimmer-google-fonts:1.0.0-alpha14")
    
    // Industrial Image Loading (No Mocks)
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
