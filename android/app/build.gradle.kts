plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------------
// Optional GPU filter path.
// Enable the MIT-licensed `org.wysaid:gpuimage-plus` library with either:
//   - gradle.properties:  facecam.gpuFilters=true
//   - or command line:    ./gradlew assembleDebug -Pfacecam.gpuFilters=true
// When the flag is OFF (the default) the dependency is not resolved at all and
// the app builds with no GPU library present - see ANDROID_SETUP.md section 7.
// ---------------------------------------------------------------------------
val gpuFiltersEnabled =
    (project.findProperty("facecam.gpuFilters") as String?)?.toBoolean() ?: false

android {
    namespace = "com.facecam.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.facecam.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Exposed to Kotlin as BuildConfig.GPU_FILTERS_ENABLED.
        buildConfigField("boolean", "GPU_FILTERS_ENABLED", gpuFiltersEnabled.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / lifecycle
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Jetpack Compose (BOM-managed)
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:6.2.1")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:22.6.0")

    // Image loading / EXIF
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Optional GPU filter path (MIT). Resolved ONLY when facecam.gpuFilters=true.
    // The GitHub project is wysaid/android-gpuimage-plus; the published Maven
    // artifact id is `gpuimage-plus` (group org.wysaid). The `-min` variant is
    // image-only (no FFmpeg), which suits an offline still-photo app.
    if (gpuFiltersEnabled) {
        implementation("org.wysaid:gpuimage-plus:3.2.0-min")
    }

    // Tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
}
