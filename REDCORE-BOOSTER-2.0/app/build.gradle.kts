import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

// Production signing: create keystore.properties next to settings.gradle.kts (never commit it):
//   storeFile=release.jks  storePassword=…  keyAlias=…  keyPassword=…
// or set REDCORE_KEYSTORE / REDCORE_KEYSTORE_PASSWORD / REDCORE_KEY_ALIAS / REDCORE_KEY_PASSWORD.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.redcore.booster"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.redcore.booster"
        minSdk = 26
        targetSdk = 35
        // 2.0.0 -> 20000 (major * 10000 + minor * 100 + patch). Must stay above 1.3.1's versionCode 5.
        versionCode = 20000
        versionName = "2.0"
    }

    signingConfigs {
        // Shared TEST key kept in the project so every machine builds upgrade-compatible test APKs.
        // It is public: never use it for a store / public production release.
        create("test") {
            storeFile = file("keystore/redcore-test.keystore")
            storePassword = "android"
            keyAlias = "redcore-test"
            keyPassword = "android"
        }
        signingValue("storeFile", "REDCORE_KEYSTORE")?.let { store ->
            create("release") {
                storeFile = rootProject.file(store)
                storePassword = signingValue("storePassword", "REDCORE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "REDCORE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "REDCORE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Your own key when configured. Otherwise the release APK is left unsigned, unless you explicitly
            // ask for a test-signed build with -Predcore.testSignRelease=true (for sideload testing only).
            signingConfig = signingConfigs.findByName("release")
                ?: if (project.hasProperty("redcore.testSignRelease")) signingConfigs.getByName("test") else null
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "3g" }
        }
    }
    lint {
        abortOnError = true
        checkDependencies = false
        // Version-bump suggestions are reported but don't fail the build; everything else that is an error does.
        informational += "GradleDependency"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    // Compose UI tests need androidx.compose.ui:ui-test-manifest (a test-only activity), which must never ship in a
    // release APK. All app logic is identical in both variants and is unit-tested in the debug variant; the pure
    // logic in :core is tested independently of any variant.
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = false
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
