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
    }
}

rootProject.name = "TrainerBattle"

include(":core")

// The Android app needs an Android SDK. Android Studio writes local.properties automatically;
// CI sets ANDROID_HOME. Without either, only the pure-Kotlin game logic in :core is built.
val hasAndroidSdk = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("No Android SDK found - skipping :app (building :core only).")
}
