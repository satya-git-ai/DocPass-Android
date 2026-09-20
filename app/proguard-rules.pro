# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Room database & entities
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep data models and security cryptographic utilities
-keep class com.example.data.** { *; }
-keep class com.example.security.** { *; }

# Keep Biometric prompt
-keep class androidx.biometric.** { *; }

# Keep Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Keep Compose Runtime
-keep class androidx.compose.runtime.** { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
