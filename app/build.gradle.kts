plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tuxungo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tuxungo.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 250
        versionName = "1.15.0"
    }

    // Release signing is injected by CI from repository secrets (the
    // keystore is restored to a temp path and passed via environment).
    // Without those variables the release build stays unsigned and local
    // debug builds are unaffected.
    signingConfigs {
        // Pinned TEST signing: without this, every CI runner generates
        // a fresh debug keystore and test builds can never be installed
        // over each other. This key is a throwaway committed on purpose
        // (standard debug alias/password); it is NOT the release key.
        create("testPin") {
            storeFile = rootProject.file("tools/test-signing.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (System.getenv("TUXUNGO_KEYSTORE_PATH") != null) {
            create("release") {
                storeFile = file(System.getenv("TUXUNGO_KEYSTORE_PATH"))
                storePassword = System.getenv("TUXUNGO_STORE_PASSWORD")
                keyAlias = System.getenv("TUXUNGO_KEY_ALIAS")
                keyPassword = System.getenv("TUXUNGO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("testPin")
        }
        release {
            isMinifyEnabled = false
            if (System.getenv("TUXUNGO_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    androidResources {
        // Keep the bundled model stored raw in the APK so openFd works and
        // the app copies the exact gzip bytes KataGo expects.
        noCompress += "model"
    }
    packaging {
        jniLibs {
            // The KataGo executable ships as libkatago.so and must be
            // extracted to nativeLibraryDir so the app can exec it.
            useLegacyPackaging = true
        }
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
}
