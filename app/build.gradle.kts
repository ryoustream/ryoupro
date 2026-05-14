import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    id("org.jetbrains.kotlin.kapt")
}

// Build metadata
val buildNumber = (System.getenv("BUILD_NUMBER") ?: project.findProperty("BUILD_NUMBER") ?: "1").toString().toIntOrNull() ?: 1
val gitHash = (System.getenv("GIT_HASH") ?: project.findProperty("GIT_HASH") ?: "local").toString()
val buildDate: String = SimpleDateFormat("yyyy.MM.dd").format(Date())

// Signing
val keystorePropertiesFile = rootProject.file("signing.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.ryoustream.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ryoustream.player"
        minSdk = 28
        targetSdk = 35
        versionCode = buildNumber
        versionName = "v1.0.0-build${buildNumber}-api35-git${gitHash}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    signingConfigs {
        create("release") {
            val ksBase64 = System.getenv("KEYSTORE_BASE64")
            if (ksBase64 != null) {
                storeFile = file("${rootProject.buildDir}/keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "defaultryoustream"
                keyAlias = System.getenv("KEY_ALIAS") ?: "ryoustream"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "defaultryoustream"
            } else if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"]?.toString() ?: "keystore.jks")
                storePassword = keystoreProperties["storePassword"]?.toString()
                keyAlias = keystoreProperties["keyAlias"]?.toString()
                keyPassword = keystoreProperties["keyPassword"]?.toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ksBase64 = System.getenv("KEYSTORE_BASE64")
            if (ksBase64 != null || keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.viewpager2)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.service)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.room.runtime)
    implementation(libs.room.guava)
    annotationProcessor(libs.room.compiler)

    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.gson)
    implementation(libs.workmanager)
    implementation(libs.preference)
    implementation(libs.mediarouter)
    implementation(libs.guava)
}
