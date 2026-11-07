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
        // Overridden by the release workflow: -PversionName=<tag without "v"> -PversionCode=<run number>
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1"
    }

    signingConfigs {
        // Only defined when the release workflow provides the keystore; local builds stay debug-signed.
        System.getenv("RIDEBRIDGE_KEYSTORE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("RIDEBRIDGE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RIDEBRIDGE_KEY_ALIAS")
                keyPassword = System.getenv("RIDEBRIDGE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
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
