# FaceCam ProGuard / R8 rules.
# The app ships with minification disabled by default; these rules are here so a
# release build with `isMinifyEnabled = true` still works.

# Keep the app's own model / preset classes (reflection-free, but safe).
-keep class com.facecam.app.film.** { *; }
-keep class com.facecam.app.data.** { *; }

# CameraX
-dontwarn androidx.camera.**

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
