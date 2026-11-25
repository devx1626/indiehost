plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.indiehost.launcher"
    compileSdk = 34

    defaultConfig {
        // Placeholder id: rename once the product has a name.
        applicationId = "com.indiehost.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0-steam"
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
            // Debug only for now: this is what gets sideloaded over adb.
            applicationIdSuffix = ""
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
}
