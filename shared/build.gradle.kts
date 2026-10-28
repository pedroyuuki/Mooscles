import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android app and a future iOS app. commonMain stays free of
// JVM and Android APIs; androidMain and iosMain hold the platform entry points.
plugins {
    // Both plugins are already on the build classpath (root buildscript and the
    // root plugins block), so they are applied here by id only.
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        // Not com.mooscles: the app already generates com.mooscles.R.
        namespace = "com.mooscles.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest { isIncludeAndroidResources = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        // JVM equivalence tests: the common replacements against the java.* APIs they replace.
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation("junit:junit:4.13.2")
        }
    }
}
