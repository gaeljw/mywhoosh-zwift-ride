import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.gael.ridebridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.gael.ridebridge"
        minSdk = 33          // Android 13+: lets us use the modern, non-deprecated BLE APIs
        targetSdk = 36       // Android 16
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// No dependencies on purpose: plain Android framework + Kotlin stdlib.
