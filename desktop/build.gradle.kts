import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
}

group = "com.tcl"
version = "0.2.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("com.google.code.gson:gson:2.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

compose.desktop {
    application {
        mainClass = "com.tcl.desktop.MainKt"

        nativeDistributions {
            // Portable app-image is what CI ships; Msi/Exe need WiX on the build machine.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "TurboCraftLauncher"
            packageVersion = "0.2.0"
            description = "TurboCraft Launcher for Windows"
            vendor = "TCL Team"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "0f3c1d9e-4c55-4b7e-9c1b-8f4c1a2d5b60"
            }
        }
    }
}
