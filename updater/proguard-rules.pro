# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep all classes for simplicity in bootstrap app
-keep class ** { *; }

# Keep all Android framework classes
-keep class android.** { *; }
-keep class androidx.** { *; }