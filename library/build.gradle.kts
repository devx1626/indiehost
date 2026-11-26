// Data layer. Room persistence lives here (M1); the module stays UI-free and
// exposes only the Game model and the GameRepository seam.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.indiehost.library"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":detect"))

    // `api`: :app constructs IndieHostDatabase, and RoomDatabase must be on its
    // compile classpath for that to resolve.
    api("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}
