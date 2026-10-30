import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing for GitHub releases, from keystore.properties (gitignored). F-Droid
// builds without it and signs with its own key.
val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "site.yaft.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "site.yaft.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            // Reproducible builds (F-Droid publishes our signed APK only if its rebuild matches).
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // MapLibre's native library comes stripped from Maven Central. Packing it as it is, rather than stripping it
    // again with whichever NDK the build machine has, keeps release builds the same everywhere.
    packaging {
        jniLibs.keepDebugSymbols += "**/libmaplibre.so"
        // Compressed, the native libraries of the four ABIs add about 15 MB to the APK instead of 42 MB.
        jniLibs.useLegacyPackaging = true
    }

    // F-Droid: no Google dependency metadata blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// Baseline profile generation isn't deterministic; skip it so rebuilds match byte for byte.
tasks.matching { it.name.contains("ArtProfile") }.configureEach { enabled = false }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    // The session view's map (BSD-2-Clause, no Play Services).
    implementation(libs.maplibre.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests; use the real one there.
    testImplementation(libs.org.json)
}
