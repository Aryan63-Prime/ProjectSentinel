# Admin ProGuard Rules
# Clean configuration — add rules only as dependencies require them.

# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Google Tink (used by EncryptedSharedPreferences)
# These are compile-time annotations not present at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Retrofit
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.**

# Moshi generated adapters
-keep class com.sentinel.admin.data.remote.api.*Dto { *; }
-keep class com.sentinel.admin.data.remote.api.*Response { *; }
-keep class com.sentinel.admin.data.remote.api.*JsonAdapter { *; }

# JNI — Opus decoder native bridge
-keep class com.sentinel.admin.data.audio.OpusDecoderJni { *; }
