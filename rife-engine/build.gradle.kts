plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// NOTE: this module intentionally has NO Jetpack Compose dependency.
// Per AGENTS.md, Compose is exclusive to UI code in :app. This module is a
// plain Kotlin + JNI engine so it stays testable/reusable independent of UI.
android {
    namespace = "com.rynime.nvplayer.rife"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")

        externalNativeBuild {
            cmake {
                // arm64-v8a is the only ABI that gets Vulkan-accelerated RIFE.
                // armeabi-v7a is built too so Mode A (batch export, CPU-only
                // ncnn fallback) still works on 32-bit devices.
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation(project.dependencies.platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android")
    // Option B backend (RifeTfliteInterpolator): classic Interpreter API,
    // not the newer CompiledModel API - resizeInput()-based dynamic shape
    // support is long-established and documented here, whereas
    // CompiledModel's dynamic-shape behavior isn't (this backend needs
    // per-video padded resolutions, not a fixed shape baked in at
    // conversion time). NNAPI delegate intentionally not added - see
    // RifeTfliteInterpolator's class doc for why.
    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
