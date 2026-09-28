// The Android Gradle plugin is declared in :app only, so :core also builds on machines
// without access to Google's Maven repository or an Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
