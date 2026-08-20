plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "al.speedline.iptv"
    compileSdk = 37

    defaultConfig {
        applicationId = "al.speedline.iptv"
        minSdk = 23
        targetSdk = 36
        versionCode = 17
        versionName = "0.3.5"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.tv:tv-foundation:1.0.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-datasource:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")

    implementation("androidx.work:work-runtime:2.11.2")

    // Full software-decoder fallback for VOD / Series.
    implementation("org.videolan.android:libvlc-all:3.7.0")

    // Optional IJK AARs placed in app/libs are picked up automatically.
    // The app compiles and runs with Media3 even when IJK binaries are absent.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
