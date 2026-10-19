plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.libre.cellinfo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.libre.cellinfo"
        minSdk = 21          // Android 5.0 en adelante
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Sin dependencias de terceros: solo el SDK de Android y la librería estándar de Kotlin.
