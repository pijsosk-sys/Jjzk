plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.boddy.os"
    compileSdk = 35

    defaultConfig {
        // Its own app id, so it installs next to Boddy instead of replacing it.
        applicationId = "com.boddy.studio"
        minSdk = 26
        // Android 9 rules: newer targets forbid running code mapped from the app's own files,
        // which the Windows compatibility layer (Hangover) needs.
        targetSdk = 28
        // CI numbers each build, so every new APK installs over the last one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        ndk { abiFilters += "arm64-v8a" }
    }

    // One fixed test key in the repo: every build has the same signature, so updates install over
    // each other and keep the Linux system and Studio downloads. Not a secret; this is a test app.
    signingConfigs {
        create("studio") {
            storeFile = file("studio-launcher.jks")
            storePassword = "studio-launcher"
            keyAlias = "studio"
            keyPassword = "studio-launcher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("studio")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Not a Play Store app: the old target (see targetSdk) is on purpose.
    lint { disable += "ExpiredTargetSdkVersion" }

    packaging {
        // proot and its loader must be extracted to nativeLibraryDir so they can be executed.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
}
