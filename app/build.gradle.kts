plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.example.test2healthapp2"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.test2healthapp2"
        minSdk = 24
        targetSdk = 35
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        mlModelBinding = true
    }
}

dependencies {
//    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation ("junit:junit:4.13.2")

    implementation ("com.squareup.okhttp3:okhttp:4.9.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation ("androidx.compose.foundation:foundation:1.5.4")
    implementation ("androidx.compose.material:material-icons-extended:1.5.4")
//    implementation ("androidx.compose.material3:material3:1.1.2")
    implementation ("androidx.navigation:navigation-compose:2.7.6")
    implementation ("androidx.activity:activity-compose:1.9.2")
    implementation ("androidx.compose.ui:ui:1.7.0")
//    implementation ("androidx.compose.material3:material3:1.3.0")
    implementation ("androidx.compose.runtime:runtime-livedata:1.7.0")
    implementation ("androidx.compose.ui:ui-viewbinding:1.7.0")
    implementation ("androidx.navigation:navigation-compose:2.8.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation ("androidx.camera:camera-core:1.3.4")
    implementation ("androidx.camera:camera-camera2:1.3.4")
    implementation ("androidx.camera:camera-lifecycle:1.3.4")
    implementation ("androidx.camera:camera-view:1.3.4")
//    implementation("androidx.camera:camera-camera2:1.4.0-beta01")
//    implementation("androidx.camera:camera-lifecycle:1.4.0-beta01")
//    implementation("androidx.camera:camera-view:1.4.0-beta01")
    implementation("org.tensorflow:tensorflow-lite-support:0.1.0") // Match your old project
    implementation("org.tensorflow:tensorflow-lite-metadata:0.1.0")          // Or the latest stable version
//    implementation("androidx.compose.material3:material3:1.3.0-beta01")
    implementation ("androidx.activity:activity-compose:1.9.2")
    implementation ("androidx.compose.material3:material3:1.3.0")
    implementation ("androidx.compose.ui:ui:1.7.0")
    implementation ("androidx.compose.runtime:runtime:1.7.0")
    implementation ("androidx.compose.foundation:foundation:1.7.0")
    implementation ("androidx.compose.ui:ui-viewbinding:1.7.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)
    implementation(libs.tensorflow.lite.gpu)
//    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}