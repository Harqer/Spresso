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
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val backendUrl: String = System.getenv("VAULTIER_BACKEND_URL") ?: "https://aura-edge-service.quantumcoin.workers.dev"
        buildConfigField("String", "VAULTIER_BACKEND_URL", "\"$backendUrl\"")

        val googleId: String = System.getenv("GOOGLE_WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleId\"")

        val internalSecret: String = System.getenv("VAULTIER_INTERNAL_SECRET") ?: ""
        val vaultierDomain: String = System.getenv("VAULTIER_DOMAIN") ?: "vaultier.wearables.com"
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
    
    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    implementation(libs.androidx.datastore.preferences)
    
    // Meta Wearables DAT SDK
    implementation("com.meta.wearable:mwdat-core:0.7.0")
    implementation("com.meta.wearable:mwdat-display:0.7.0")
    implementation("com.meta.wearable:mwdat-camera:0.7.0")
    debugImplementation("com.meta.wearable:mwdat-mockdevice:0.7.0")
    
    implementation("androidx.xr.glimmer:glimmer:1.0.0-alpha14")
    implementation("androidx.xr.glimmer:glimmer-google-fonts:1.0.0-alpha14")
    
    implementation(libs.coil.compose)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
