# Aura Retail Proguard Rules

# Meta Wearables DAT SDK
-keep class com.meta.wearable.dat.** { *; }
-dontwarn com.meta.wearable.dat.**

# Firebase Identity Pulse
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.common.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# Removed redundant Compose and Coroutine rules (Handled by AGP 9 and Compose Compiler)

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
