-keepattributes Signature
-keepattributes *Annotation*

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Tink (used by security-crypto / EncryptedSharedPreferences)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.crypto.tink.**

