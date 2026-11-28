plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.indiehost.launcher"
    compileSdk = 34

    // Release signing is opt-in: the keystore is never in this repository, and
    // CI (which has no key) still builds the debug APK. The four variables are
    // supplied by the local signing environment before a release build.
    val keystorePath = System.getenv("INDIEHOST_KEYSTORE")
    val storePassword = System.getenv("INDIEHOST_STORE_PASSWORD")
    val keyPassword = System.getenv("INDIEHOST_KEY_PASSWORD")
    val keyAlias = System.getenv("INDIEHOST_KEY_ALIAS") ?: "indiehost"
    val hasReleaseKey = keystorePath != null && storePassword != null && keyPassword != null

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                this.storePassword = storePassword!!
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword!!
            }
        }
    }

    defaultConfig {
        // Placeholder id: rename once the product has a name.
        applicationId = "com.indiehost.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "0.4.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        getByName("debug") {
            // Debug is still what CI builds and what gets sideloaded over adb.
            applicationIdSuffix = ""
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(project(":library"))
    implementation(project(":detect"))
    implementation(project(":runtimes"))

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.webkit:webkit:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
