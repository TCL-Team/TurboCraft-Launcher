import java.io.File
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

/**
 * Discord Social SDK (proprietary binary, NOT committed to this repository).
 *
 * The AAR is fetched on demand so the GPL source tree stays free of a redistributed
 * proprietary library, and so the repository does not carry a ~30 MB blob. Point
 * `discordSdkAarUrl` / the `DISCORD_SDK_AAR_URL` environment variable elsewhere, or drop a
 * file at `DiscordRPC/libs/discord_partner_sdk.aar`, to use a different SDK build.
 */
val defaultSdkUrl =
    "https://github.com/Endiq-jar/TurtleLauncher/raw/" +
        "a0abe4f180add14b9286cfcf27de6f51aadd19a1/TurtleLauncher/libs/discord_partner_sdk.aar"
val sdkUrl = (project.findProperty("discordSdkAarUrl") as String?)
    ?: System.getenv("DISCORD_SDK_AAR_URL")
    ?: defaultSdkUrl

val sdkAar = layout.projectDirectory.file("libs/discord_partner_sdk.aar").asFile
if (!sdkAar.exists() || sdkAar.length() < 1_000_000L) {
    sdkAar.parentFile.mkdirs()
    logger.lifecycle("DiscordRPC: downloading Discord Social SDK from $sdkUrl")
    val temp = File(sdkAar.parentFile, "${sdkAar.name}.part")
    URI(sdkUrl).toURL().openStream().use { input ->
        temp.outputStream().use { output -> input.copyTo(output) }
    }
    check(temp.length() > 1_000_000L) { "Discord Social SDK download looks truncated: ${temp.length()} bytes" }
    if (!temp.renameTo(sdkAar)) {
        temp.copyTo(sdkAar, overwrite = true)
        temp.delete()
    }
}

/** Prefab package + per-ABI libraries staged out of the AAR for the CMake build. */
val discordSdkDir = layout.buildDirectory.dir("discord-social-sdk")
val prepareDiscordSdk by tasks.registering(Sync::class) {
    val sdk = sdkAar
    from(zipTree(sdk)) { include("prefab/**") }
    into(discordSdkDir)
}

/** Same single-arch selection the :TCL APK build uses (-Darch=arm64, ...). */
val projectArch: String = System.getProperty("arch", "all")

android {
    namespace = "com.movtery.zalithlauncher.discord"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        if (projectArch != "all") {
            ndk {
                abiFilters += when (projectArch) {
                    "arm" -> "armeabi-v7a"
                    "arm64" -> "arm64-v8a"
                    "x86" -> "x86"
                    "x86_64" -> "x86_64"
                    else -> projectArch
                }
            }
        }
        externalNativeBuild {
            cmake {
                // Same STL as the rest of the app (TCL's ndkBuild uses c++_shared).
                arguments += "-DANDROID_STL=c++_shared"
                arguments += "-DDISCORD_SDK_DIR=${discordSdkDir.get().asFile.absolutePath}"
                targets += "tcdiscordrpc"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    ndkVersion = "25.2.9519653"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The SDK AAR ships its own Prefab package; resolving it through Prefab as well would
    // import unrelated native modules, so only the staged copy is used.
    buildFeatures {
        prefab = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libdiscord_partner_sdk.so")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Java side of the SDK: DiscordSocialSdkInit (engine activity) and the RPC plumbing.
    implementation(files(sdkAar))
}

// CMake configuration can run independently of preBuild, so every task that touches the
// staged SDK or the native build depends on the staging task.
tasks.configureEach {
    if (name == "preBuild" || name.startsWith("configureCMake") || name.startsWith("buildCMake") ||
        (name.startsWith("merge") && name.endsWith("JniLibFolders"))
    ) {
        dependsOn(prepareDiscordSdk)
    }
}
