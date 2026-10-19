plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.standbyclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.standbyclock"
        // Android 12 and newer; the S24 Ultra ships with Android 14 or later.
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// No AndroidX or Compose dependencies: the UI is plain Views, so the build
// needs only the Android SDK platform and the Android Gradle plugin.
