pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Optional GPU filter path. The wysaid Maven repo hosts the MIT-licensed
        // org.wysaid:gpuimage-plus artifact. It is only consulted when the GPU
        // feature flag is enabled (see app/build.gradle.kts / ANDROID_SETUP.md).
        val gpuFilters = providers.gradleProperty("facecam.gpuFilters")
            .map { it.toBoolean() }
            .getOrElse(false)
        if (gpuFilters) {
            maven { url = uri("https://maven.wysaid.org/") }
        }
    }
}

rootProject.name = "FaceCam"
include(":app")
