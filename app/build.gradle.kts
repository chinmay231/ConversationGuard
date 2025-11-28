plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ai.guard2"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.guard2"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    // Make Java & Kotlin targets consistent
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    // Optional with kotlin-compose plugin; safe to leave commented
    /*
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    */
}

dependencies {
    // Compose BOM from version catalog
    implementation(platform(libs.androidx.compose.bom))

    // Compose + Activity
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Core / lifecycle / work / coroutines
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Material lib that provides Theme.Material3.DayNight.NoActionBar
    implementation(libs.google.material)

    // TensorFlow Lite runtime + GPU delegate
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
