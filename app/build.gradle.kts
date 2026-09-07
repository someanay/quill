import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release-signing secrets live in local.properties (git-ignored) or env vars — NEVER in VCS.
// We only ever READ them; values are never logged. If any are absent the release signingConfig is
// omitted gracefully so debug + CI-without-secrets still build (see buildTypes.release below).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String? =
    (localProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

android {
    namespace = "com.tihor.quill"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tihor.quill"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.gradleProperty("quillVersionCode").orNull?.toInt() ?: 3
        versionName = providers.gradleProperty("quillVersionName").orNull ?: "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing — only created when every secret is present AND the keystore file exists, so a
    // machine without the secrets (CI, a fresh clone) still builds (it just produces an unsigned
    // release). No credential value is ever hardcoded or printed.
    signingConfigs {
        val ksPath = secret("QUILL_KEYSTORE_PATH")
        val ksPass = secret("QUILL_KEYSTORE_PASSWORD")
        val kAlias = secret("QUILL_KEY_ALIAS")
        val kPass = secret("QUILL_KEY_PASSWORD")
        if (ksPath != null && ksPass != null && kAlias != null && kPass != null && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        release {
            // R8 on from M5 (shift-left): catch serialization/reflection stripping early, not at M12.
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the release signingConfig when it was created (secrets present); else leave unsigned.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            if (providers.gradleProperty("requireReleaseSigning").orNull == "true" && signingConfig == null) {
                throw GradleException("Release signing is not configured. Set the QUILL_KEYSTORE_* and QUILL_KEY_* values in local.properties or the environment.")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric
            isReturnDefaultValues = true     // android.util.Log etc. return defaults, don't throw
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Async
    implementation(libs.kotlinx.coroutines.android)

    // JSON + networking (STT, spec §3.3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Settings persistence (spec §4.1)
    implementation(libs.androidx.datastore.preferences)


    // Demo-video carousel (Home + onboarding steps): muted looping MP4s, one ExoPlayer per carousel,
    // rendered in Compose via media3-ui-compose's PlayerSurface.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Regenerates the repo-root copy-manifest.json — the machine-readable Copy Catalog export the external
// copy-authoring tool (quill-copy-tool) consumes. CopyManifestTest guards it: the committed file must
// match this task's output byte-for-byte, so copy can't drift from CopyKeys / strings.default.json
// without a red build (same guard pattern as NoComposeStringLiteralsTest). The generator lives in the
// unit-test source set (never shipped in the APK), so we run it on that classpath.
tasks.register<JavaExec>("generateCopyManifest") {
    group = "copy"
    description = "Regenerate /copy-manifest.json from CopyKeys + strings.default.json (run after changing copy)."
    dependsOn("compileDebugUnitTestKotlin")
    // Reuse the unit-test runtime classpath (main + test classes + deps), but via a plain provider so we
    // don't take a task dependency on testDebugUnitTest itself — otherwise generating the file would run
    // the very test that guards it, before the file exists.
    val unitTest = tasks.named<Test>("testDebugUnitTest")
    classpath = files(provider { unitTest.get().classpath })
    mainClass.set("com.tihor.quill.copy.CopyManifestKt")
    workingDir = projectDir
}
