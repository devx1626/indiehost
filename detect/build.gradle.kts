// Pure Kotlin/JVM: no Android APIs, so the whole engine-fingerprinting layer
// is unit-testable on the host without a device or emulator.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
