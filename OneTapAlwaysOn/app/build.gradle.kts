plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.onetap.alwayson"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = file("../../keystore/release.keystore")
            storePassword = "djzrs2026"
            keyAlias = "djzrs"
            keyPassword = "djzrs2026"
            storeType = "PKCS12"
        }
    }

    defaultConfig {
        applicationId = "com.onetap.alwayson"
        minSdk = 26          // TYPE_APPLICATION_OVERLAY 从 API 26 起可用，省去兼容分支
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}