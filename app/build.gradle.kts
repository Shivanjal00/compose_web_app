plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.module.webview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.module.webview"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val media3_version = "1.9.0"

    // Core Android & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Media3 ExoPlayer - Essential dependencies only
    implementation("androidx.media3:media3-exoplayer:${media3_version}")
    implementation("androidx.media3:media3-ui:${media3_version}")
    implementation("androidx.media3:media3-common:${media3_version}")

    // Media3 - Streaming format support
    implementation("androidx.media3:media3-exoplayer-dash:${media3_version}")
    implementation("androidx.media3:media3-exoplayer-hls:${media3_version}")

    implementation("androidx.compose.material:material-icons-extended")


    // Optional: Add these only if you need specific features
    // implementation("androidx.media3:media3-exoplayer-rtsp:${media3_version}")
    // implementation("androidx.media3:media3-datasource-okhttp:${media3_version}")
}