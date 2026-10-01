# FaceCam ProGuard / R8 rules.
# The app ships with minification disabled by default; these rules are here so a
# release build with `isMinifyEnabled = true` still works.

# Keep the app's own model / preset classes (reflection-free, but safe).
-keep class com.facecam.app.film.** { *; }
-keep class com.facecam.app.data.** { *; }

# Google Play Billing
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# AdMob / Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# CameraX
-dontwarn androidx.camera.**

# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
