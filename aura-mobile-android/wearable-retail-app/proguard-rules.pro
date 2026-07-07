# Aura Retail Proguard Rules

# Meta Wearables DAT SDK
-keep class com.meta.wearable.dat.** { *; }
-dontwarn com.meta.wearable.dat.**

# Firebase Identity Pulse (Replacing Clerk)
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.internal.** { *; }
-dontwarn com.google.android.gms.internal.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# Jetpack Compose
-keepclassmembers class * extends androidx.compose.runtime.Composer { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Sentry
-keepattributes LineNumberTable,SourceFile
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# Cloudflare Turnstile (React/WebView Bridge)
-keep class android.webkit.WebView { *; }
-keep interface android.webkit.** { *; }
-keepattributes JavascriptInterface
