/*
 * TurboCraft Launcher — desktop (Windows) edition
 * Copyright (C) 2025-2026 TCL Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.tcl.desktop.core

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android edition jaisa loader picker */
enum class LoaderType(val label: String) {
    VANILLA("Vanilla"),
    FABRIC("Fabric"),
    QUILT("Quilt"),
    NEOFORGE("NeoForge"),
    FORGE("Forge"),
    OPTIFINE("OptiFine"),
}

data class LoaderVersion(val version: String, val stable: Boolean = true) {
    val label: String get() = version
}

object Loaders {

    /** Selected base version ke liye available loader versions */
    suspend fun list(type: LoaderType, mcVersion: String): List<LoaderVersion> = withContext(Dispatchers.IO) {
        when (type) {
            LoaderType.VANILLA -> emptyList()
            LoaderType.FABRIC -> fabricVersions(mcVersion)
            LoaderType.QUILT -> quiltVersions(mcVersion)
            LoaderType.NEOFORGE -> neoForgeVersions(mcVersion)
            LoaderType.FORGE -> forgeVersions(mcVersion)
            LoaderType.OPTIFINE -> emptyList()
        }
    }

    /**
     * Install — naya version id lautaata hai.
     * `versions/<id>` ban jaata hai aur `inheritsFrom` base par set hota hai.
     */
    suspend fun install(
        type: LoaderType,
        mcVersion: String,
        loaderVersion: String? = null,
        onProgress: (InstallProgress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        when (type) {
            LoaderType.VANILLA -> mcVersion
            LoaderType.FABRIC -> FabricInstaller.install(mcVersion, loaderVersion, onProgress = onProgress)
            LoaderType.QUILT -> QuiltInstaller.install(mcVersion, loaderVersion, onProgress = onProgress)
            LoaderType.NEOFORGE -> NeoForgeInstaller.install(mcVersion, loaderVersion, onProgress = onProgress)
            LoaderType.FORGE -> ForgeInstaller.install(mcVersion, loaderVersion, onProgress = onProgress)
            LoaderType.OPTIFINE -> error("OptiFine support coming soon")
        }
    }

    private fun gson() = MojangApi.gson()

    private suspend fun fabricVersions(mcVersion: String): List<LoaderVersion> {
        val json = Net.getString("https://meta.fabricmc.net/v2/versions/loader/$mcVersion")
        return gson().fromJson(json, Array<JsonObject>::class.java).mapNotNull { entry ->
            val loader = entry.getAsJsonObject("loader") ?: return@mapNotNull null
            val version = loader.get("version")?.asString ?: return@mapNotNull null
            LoaderVersion(version, loader.get("stable")?.asBoolean ?: false)
        }
    }

    private suspend fun quiltVersions(mcVersion: String): List<LoaderVersion> {
        val json = Net.getString("https://meta.quiltmc.org/v3/versions/loader/$mcVersion")
        return gson().fromJson(json, Array<JsonObject>::class.java).mapNotNull { entry ->
            val loader = entry.getAsJsonObject("loader") ?: return@mapNotNull null
            loader.get("version")?.asString?.let { LoaderVersion(it, true) }
        }
    }

    /** NeoForge maven-metadata se versions (1.20.2+) */
    private suspend fun neoForgeVersions(mcVersion: String): List<LoaderVersion> {
        val url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
        val xml = runCatching { Net.getString(url) }.getOrNull() ?: return emptyList()
        val all = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()
        // NeoForge version format: <mcMinor>.<mcPatch>.<loader> e.g. 21.1.72 for 1.21.1
        val parts = mcVersion.split(".")
        val prefix = if (parts.size >= 3) "${parts[1]}.${parts[2]}." else return emptyList()
        return all.filter { it.startsWith(prefix) }
            .map { LoaderVersion(it, !it.contains("-beta") && !it.contains("-alpha")) }
            .reversed()
    }

    /** Forge maven-metadata se versions */
    private suspend fun forgeVersions(mcVersion: String): List<LoaderVersion> {
        val url = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
        val xml = runCatching { Net.getString(url) }.getOrNull() ?: return emptyList()
        val all = Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()
        return all.filter { it.startsWith("$mcVersion-") }
            .map { LoaderVersion(it.removePrefix("$mcVersion-"), true) }
            .reversed()
    }
}

/**
 * Quilt — Fabric jaisa hi; profile json meta.quiltmc.org se.
 */
object QuiltInstaller {
    private const val META = "https://meta.quiltmc.org/v3/versions/loader"

    suspend fun install(
        gameVersion: String,
        loaderVersion: String? = null,
        onProgress: (InstallProgress) -> Unit = {},
        installer: GameInstaller = GameInstaller(),
    ): String = withContext(Dispatchers.IO) {
        AppPaths.ensure()
        require(AppPaths.versionJson(gameVersion).isFile) { "Install Minecraft $gameVersion first" }

        val loader = loaderVersion ?: run {
            val list = runCatching { Loaders.list(LoaderType.QUILT, gameVersion) }.getOrDefault(emptyList())
            list.firstOrNull()?.version ?: error("No Quilt loader for $gameVersion")
        }
        val id = "quilt-loader-$loader-$gameVersion"

        onProgress(InstallProgress(stage = "Quilt $loader profile…", filesTotal = 1))
        val profileJson = Net.getString("$META/$gameVersion/$loader/profile/json")
        val dir = AppPaths.versionDir(id).also { it.mkdirs() }
        AppPaths.versionJson(id).writeText(profileJson)

        val profile = MojangApi.parse(profileJson)
        installer.installLibrariesOf(profile, id, "Quilt libraries…", onProgress)
        onProgress(InstallProgress(stage = "Done", filesDone = 1, filesTotal = 1))
        id
    }
}

/**
 * Forge / NeoForge — official installer jar ko headless chala kar profile banate hain
 * (`--installClient <dir>`), phir usi folder ka version json use karte hain.
 */
object ForgeInstaller {
    private const val FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"

    suspend fun install(
        gameVersion: String,
        loaderVersion: String? = null,
        onProgress: (InstallProgress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val version = loaderVersion ?: run {
            val list = runCatching { Loaders.list(LoaderType.FORGE, gameVersion) }.getOrDefault(emptyList())
            list.firstOrNull()?.version ?: error("No Forge build for $gameVersion")
        }
        val full = "$gameVersion-$version"
        installJarBased(
            label = "Forge",
            installerUrl = "$FORGE_MAVEN/$full/forge-$full-installer.jar",
            expectedId = "$gameVersion-forge-$version",
            gameVersion = gameVersion,
            onProgress = onProgress
        )
    }

    /**
     * Installer jar ko `--installClient` ke saath chalata hai.
     * Minecraft ka game folder hi launcher dir hota hai (usme `versions/` hai).
     */
    internal suspend fun installJarBased(
        label: String,
        installerUrl: String,
        expectedId: String,
        gameVersion: String,
        onProgress: (InstallProgress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        AppPaths.ensure()
        require(AppPaths.versionJson(gameVersion).isFile) { "Install Minecraft $gameVersion first" }

        val java = JavaManager.ensure(gameVersion, null, "", {})
        val cache = File(AppPaths.root, "cache/installers").also { it.mkdirs() }
        val jar = File(cache, "$label-${expectedId}.jar")

        onProgress(InstallProgress(stage = "Downloading $label installer…", filesTotal = 1))
        Net.download(installerUrl, jar)

        // Installer ko launcher folder chahiye jisme launcher_profiles.json ho
        val launcherDir = AppPaths.game
        val profiles = File(launcherDir, "launcher_profiles.json")
        if (!profiles.isFile) {
            profiles.writeText("""{"profiles":{},"settings":{},"version":3}""")
        }

        onProgress(InstallProgress(stage = "Running $label installer (headless)…", filesTotal = 1))
        val process = ProcessBuilder(
            java.javaExe.absolutePath,
            "-jar",
            jar.absolutePath,
            "--installClient",
            launcherDir.absolutePath
        ).directory(launcherDir).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        jar.delete()

        val installed = AppPaths.versionJson(expectedId)
        if (code != 0 || !installed.isFile) {
            val hint = output.lines().lastOrNull { it.isNotBlank() } ?: "no output"
            error("$label installer failed (exit $code): $hint")
        }

        onProgress(InstallProgress(stage = "Done", filesDone = 1, filesTotal = 1))
        expectedId
    }
}

object NeoForgeInstaller {
    private const val MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"

    suspend fun install(
        gameVersion: String,
        loaderVersion: String? = null,
        onProgress: (InstallProgress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val version = loaderVersion ?: run {
            val list = runCatching { Loaders.list(LoaderType.NEOFORGE, gameVersion) }.getOrDefault(emptyList())
            list.firstOrNull()?.version ?: error("No NeoForge build for $gameVersion")
        }
        ForgeInstaller.installJarBased(
            label = "NeoForge",
            installerUrl = "$MAVEN/$version/neoforge-$version-installer.jar",
            expectedId = "neoforge-$version",
            gameVersion = gameVersion,
            onProgress = onProgress
        )
    }
}
