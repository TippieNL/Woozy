import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Optional developer overrides live in secrets.properties (git-ignored). Nothing in Phase 1/2
// needs an API key; the file exists so later providers (traffic, backend) never get hardcoded.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String, default: String): String =
    (secrets.getProperty(name) ?: providers.gradleProperty(name).orNull ?: default)

android {
    namespace = "app.woozy"
    compileSdk {
        // Compose 1.13 (BOM 2026.11) requires API 37.1 to compile against.
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "app.woozy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MAP_STYLE_TILES_URL", "\"${secret("MAP_TILES_URL", "https://tiles.openfreemap.org/planet")}\"")
        buildConfigField("String", "MAP_GLYPHS_URL", "\"${secret("MAP_GLYPHS_URL", "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf")}\"")
        buildConfigField("String", "GEOCODER_BASE_URL", "\"${secret("GEOCODER_BASE_URL", "https://photon.komoot.io/")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so `assembleRelease` produces an installable APK out of the box.
            // Replace with a real signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.maplibre.android)
    implementation(libs.play.services.location)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging) // used only when BuildConfig.DEBUG; R8 strips it from release

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
