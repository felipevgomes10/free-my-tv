import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

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

// The FMTV Controller page, pinned in gradle.properties: downloaded from its GitHub release,
// checked against its SHA-256 and unpacked into the APK's assets (web-remote/), with the
// protocol's JSON examples for the bridge's tests (free-my-tv-remote-controller's ADR 0002).
val webRemoteOut = layout.buildDirectory.dir("generated/webRemote")
val webRemotePage = tasks.register("webRemotePage") {
    val version = providers.gradleProperty("webRemoteVersion").orElse("")
    val sha256 = providers.gradleProperty("webRemoteSha256").orElse("")
    val localZip = providers.gradleProperty("webRemoteZip").orElse("")
    inputs.property("version", version)
    inputs.property("sha256", sha256)
    inputs.property("localZip", localZip)
    outputs.dir(webRemoteOut)
    // A local zip changes under the same path between builds: always unpack it again.
    outputs.upToDateWhen { localZip.get().isBlank() }
    doLast {
        val out = webRemoteOut.get().asFile
        out.deleteRecursively()
        val zip: File = when {
            localZip.get().isNotBlank() -> file(localZip.get())
            version.get().isNotBlank() -> {
                val cached = layout.buildDirectory.file("webRemote/web-remote-${version.get()}.zip").get().asFile
                if (!cached.exists()) {
                    cached.parentFile.mkdirs()
                    val url = "https://github.com/felipevgomes10/free-my-tv-remote-controller/releases/download/v${version.get()}/web-remote-${version.get()}.zip"
                    URI(url).toURL().openStream().use { input -> cached.outputStream().use { input.copyTo(it) } }
                }
                val actual = MessageDigest.getInstance("SHA-256").digest(cached.readBytes()).joinToString("") { "%02x".format(it) }
                if (actual != sha256.get().trim().lowercase()) {
                    cached.delete()
                    throw GradleException("web-remote-${version.get()}.zip has SHA-256 $actual, not the pinned ${sha256.get()}")
                }
                cached
            }
            else -> {
                logger.warn("webRemoteVersion is empty: building the Home without the FMTV Controller page.")
                return@doLast
            }
        }
        ZipFile(zip).use { archive ->
            for (entry in archive.entries()) {
                if (entry.isDirectory) continue
                val target = when {
                    entry.name.startsWith("page/") -> File(out, "assets/web-remote/" + entry.name.removePrefix("page/"))
                    entry.name.startsWith("protocol/") -> File(out, "test/web-remote-protocol/" + entry.name.removePrefix("protocol/"))
                    else -> continue
                }
                target.parentFile.mkdirs()
                archive.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
    }
}
android.sourceSets.getByName("main").assets.srcDir(webRemoteOut.map { it.dir("assets") })
android.sourceSets.getByName("test").resources.srcDir(webRemoteOut.map { it.dir("test") })
tasks.named("preBuild") { dependsOn(webRemotePage) }

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
    implementation(libs.dadb)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    // org.json is Android's own; on the JVM, the bridge's tests need the real one, not android.jar's stubs.
    testImplementation("org.json:json:20240303")
    testImplementation(libs.kotlinx.coroutines.test)
}
