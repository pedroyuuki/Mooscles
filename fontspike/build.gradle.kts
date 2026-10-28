import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Throwaway spike for phase 2 (branch spike/phase2-tnum, never merged): measures
// tabular figures and the Inter weight axis with Compose Multiplatform on the iOS
// simulator and the Android emulator. Same module shape as the planned :designsystem.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    android {
        namespace = "com.mooscles.fontspike"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
            implementation("org.jetbrains.compose.ui:ui:1.12.2")
            implementation("org.jetbrains.compose.components:components-resources:1.12.2")
        }
        // The spike tests are UI tests (runComposeUiTest): they run on the iOS simulator
        // and on the emulator, never on the JVM host (that would need Robolectric).
        iosTest {
            kotlin.srcDir("src/spikeTest/kotlin")
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.compose.ui:ui-test:1.12.2")
            }
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/spikeTest/kotlin")
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.compose.ui:ui-test:1.12.2")
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test.ext:junit:1.3.0")
                implementation("androidx.compose.ui:ui-test-junit4:1.12.1")
                implementation("androidx.compose.ui:ui-test-manifest:1.12.1")
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.mooscles.fontspike.resources"
    generateResClass = always
}
