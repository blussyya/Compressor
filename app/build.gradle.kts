plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.karaza.squish"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.karaza.squish"
        minSdk = 23          // Transformer's floor
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing comes entirely from environment variables — never hardcoded
    // and never committed. Locally that means exporting them yourself before running
    // assembleRelease; in CI, release.yml populates them from repo secrets. If they're
    // not set (a fresh clone with no keystore around), the release build type just
    // comes out unsigned instead of failing the build.
    val keystorePath = System.getenv("SQUISH_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SQUISH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SQUISH_KEY_ALIAS")
                keyPassword = System.getenv("SQUISH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    }
}

dependencies {
    // Check https://developer.android.com/jetpack/androidx/releases/media3
    // and bump if there's something newer.
    val media3 = "1.11.0"
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
