import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Branding — sab kuch desktop/gradle.properties se aata hai.
//  Naam/version badalna ho to sirf wahi file edit karo, yahan kuch nahi.
// ─────────────────────────────────────────────────────────────────────────────
fun prop(key: String): String =
    (project.findProperty(key) as? String)?.takeIf { it.isNotBlank() }
        ?: error("gradle.properties me \"$key\" set nahi hai")

val launcherAppName = prop("launcher_app_name")      // "TurboCraft Launcher"
val launcherName = prop("launcher_name")             // "TurboCraftLauncher"
val launcherShortName = prop("launcher_short_name")  // "TurboCraft"
val launcherVersionName = prop("launcher_version_name")
val launcherVersionCode = prop("launcher_version_code").toIntOrNull()
    ?: error("launcher_version_code integer hona chahiye")
val launcherUrl = prop("url_home")
val launcherVendor = prop("launcher_vendor")

group = "com.tcl"
version = launcherVersionName

// Generated Brand.kt — compile se pehle banti hai (build/generated/brand).
val brandSourceDir = layout.buildDirectory.dir("generated/brand/kotlin")

val generateBrand = tasks.register("generateBrand") {
    val outDir = brandSourceDir
    // inputs isliye ki property badalne par task dobara chale
    inputs.property("appName", launcherAppName)
    inputs.property("name", launcherName)
    inputs.property("shortName", launcherShortName)
    inputs.property("versionName", launcherVersionName)
    inputs.property("versionCode", launcherVersionCode)
    inputs.property("url", launcherUrl)
    inputs.property("vendor", launcherVendor)
    outputs.dir(outDir)

    doLast {
        fun kotlinString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val directory = outDir.get().asFile.resolve("com/tcl/desktop")
        directory.mkdirs()
        directory.resolve("Brand.kt").writeText(
            """
            |// AUTO-GENERATED — desktop/gradle.properties se banti hai. Isko edit mat karo.
            |package com.tcl.desktop
            |
            |/**
            | * Launcher ki poori identity ek jagah: naam, version, links, vendor.
            | * Naam ya version badalna ho to `desktop/gradle.properties` edit karo.
            | */
            |object Brand {
            |    /** UI me dikhne wala poora naam */
            |    const val APP_NAME = ${kotlinString(launcherAppName)}
            |
            |    /** Bina space wala naam — exe, installer, data folder, user-agent */
            |    const val IDENTIFIER = ${kotlinString(launcherName)}
            |
            |    /** Chhota naam — logo initials, bite-sized labels */
            |    const val SHORT_NAME = ${kotlinString(launcherShortName)}
            |
            |    const val VERSION_NAME = ${kotlinString(launcherVersionName)}
            |    const val VERSION_CODE = $launcherVersionCode
            |
            |    const val HOME_URL = ${kotlinString(launcherUrl)}
            |    const val VENDOR = ${kotlinString(launcherVendor)}
            |
            |    /** "1.2.3 (4)" — logs/about me */
            |    const val VERSION_LABEL = "${'$'}VERSION_NAME (${'$'}VERSION_CODE)"
            |
            |    /** HTTP User-Agent */
            |    const val USER_AGENT = "${'$'}IDENTIFIER/${'$'}VERSION_NAME (desktop)"
            |
            |    /** Logo ke initials — SHORT_NAME ke shabd se ("TurboCraft" -> "TC") */
            |    val INITIALS: String = SHORT_NAME
            |        .split(' ', '-', '_')
            |        .filter { it.isNotBlank() }
            |        .take(2)
            |        .joinToString("") { it.first().uppercase() }
            |        .ifBlank { SHORT_NAME.take(2).uppercase() }
            |}
            """.trimMargin()
        )
    }
}

kotlin {
    jvmToolchain(21)
    sourceSets["main"].kotlin.srcDir(generateBrand)
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
            // Portable app-image is what CI ships; Msi needs WiX on the build machine.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = launcherName
            packageVersion = launcherVersionName
            description = "$launcherAppName for Windows"
            vendor = launcherVendor

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Installer upgrades: ye UUID kabhi na badlo, warna update naya app ban jaata hai
                upgradeUuid = "0f3c1d9e-4c55-4b7e-9c1b-8f4c1a2d5b60"
            }
        }
    }
}
