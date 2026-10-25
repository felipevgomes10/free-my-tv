plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.freemytv.home"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.freemytv.home"
        minSdk = 28
        targetSdk = 35
        // Bumped by scripts/release.sh, which publishes the release the Home updates itself from.
        versionCode = providers.gradleProperty("homeVersionCode").get().toInt()
        versionName = providers.gradleProperty("homeVersionName").get()
        resourceConfigurations += listOf("pt-rBR")
    }

    buildTypes {
        release {
            // The TV runs this build: the debug one is several times slower on its CPU. It keeps the
            // debug key the Home was first installed with, so updates install over it.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release optimizations with debug-only weather fixtures/diagnostics, for the emulator.
        // Kept separate so the published release never includes the exported preview Activity.
        create("weatherBenchmark") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
    }

    sourceSets.getByName("weatherBenchmark") {
        java.srcDir("src/debug/java")
        res.srcDir("src/debug/res")
        manifest.srcFile("src/debug/AndroidManifest.xml")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.palette)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    implementation(libs.kotlinx.coroutines.android)
    // The APK Drop QR code.
    implementation(libs.zxing.core)
    implementation(libs.media3.exoplayer)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
