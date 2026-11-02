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
import java.util.zip.ZipFile

/** Modpack metadata — `versions/<id>/pack.json` */
data class PackMeta(
    val projectId: String = "",
    val slug: String = "",
    val name: String = "",
    val versionName: String = "",
    val iconUrl: String? = null,
    val mcVersion: String = "",
    val loader: String = "",
    val loaderVersion: String = "",
)

object Packs {
    fun read(versionId: String): PackMeta? {
        val file = AppPaths.packMetaFile(versionId)
        if (!file.isFile) return null
        return runCatching {
            MojangApi.gson().fromJson(file.readText(), PackMeta::class.java)
        }.getOrNull()
    }

    fun write(versionId: String, meta: PackMeta) {
        AppPaths.versionDir(versionId).mkdirs()
        runCatching {
            AppPaths.packMetaFile(versionId).writeText(MojangApi.gson().toJson(meta))
        }
    }

    /** Installed versions ka display naam */
    fun displayName(versionId: String): String = read(versionId)?.name?.takeIf { it.isNotBlank() } ?: versionId
}

/**
 * Modrinth modpack (.mrpack) install — asli TCL mobile jaisa:
 * base version + loader install, phir sab mods + overrides ek isolated folder me
 * (`versions/<packId>/game`), taaki doosre versions kharab na hon.
 */
object ModpackInstaller {

    suspend fun install(
        projectId: String,
        version: ProjectVersion,
        onProgress: (InstallProgress) -> Unit = {},
        onStatus: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        AppPaths.ensure()
        val file = version.primaryFile ?: error("This modpack version has no downloadable file")

        val cache = File(AppPaths.root, "cache/modpacks").also { it.mkdirs() }
        val local = File(cache, file.filename)

        onProgress(InstallProgress(stage = "Downloading ${file.filename}", bytesTotal = file.size))
        var done = 0L
        Net.download(file.url, local, file.sha1) { chunk ->
            done += chunk
            onProgress(
                InstallProgress(
                    stage = "Downloading ${file.filename}",
                    bytesDone = done,
                    bytesTotal = file.size
                )
            )
        }

        // .mrpack = zip with modrinth.index.json
        val indexJson = ZipFile(local).use { zip ->
            val entry = zip.getEntry("modrinth.index.json")
                ?: error("This file is not a Modrinth modpack (.mrpack)")
            zip.getInputStream(entry).bufferedReader().readText()
        }
        val index = MojangApi.gson().fromJson(indexJson, JsonObject::class.java)

        val deps = index.getAsJsonObject("dependencies")
        val mcVersion = deps?.get("minecraft")?.asString ?: error("Modpack me Minecraft version nahi hai")
        val loaderName = when {
            deps?.has("fabric-loader") == true -> "fabric"
            deps?.has("quilt-loader") == true -> "quilt"
            deps?.has("neoforge") == true -> "neoforge"
            deps?.has("forge") == true -> "forge"
            else -> "vanilla"
        }
        val loaderVersion = when (loaderName) {
            "fabric" -> deps?.get("fabric-loader")?.asString
            "quilt" -> deps?.get("quilt-loader")?.asString
            "neoforge" -> deps?.get("neoforge")?.asString
            "forge" -> deps?.get("forge")?.asString
            else -> null
        }.orEmpty()

        val packName = index.get("name")?.asString ?: version.name
        val packVersion = index.get("versionId")?.asString ?: version.versionNumber
        val slug = projectId.take(16)

        // 1. Base Minecraft version
        val installer = GameInstaller()
        if (AppPaths.versionJson(mcVersion).let { !it.isFile }) {
            onStatus("Installing Minecraft $mcVersion")
            installer.install(mcVersion) { p -> onProgress(p) }
        }

        // 2. Loader
        val loaderType = when (loaderName) {
            "fabric" -> LoaderType.FABRIC
            "quilt" -> LoaderType.QUILT
            "neoforge" -> LoaderType.NEOFORGE
            "forge" -> LoaderType.FORGE
            else -> LoaderType.VANILLA
        }
        val parentId = if (loaderType == LoaderType.VANILLA) {
            mcVersion
        } else {
            onStatus("Installing ${loaderType.label} $loaderVersion")
            Loaders.install(loaderType, mcVersion, loaderVersion.takeIf { it.isNotBlank() }, onProgress)
        }

        // 3. Pack version entry — apna game folder, taaki isolated rahe
        val packId = "pack-$slug-$packVersion".replace(Regex("[^A-Za-z0-9._-]"), "-")
        val versionDir = AppPaths.versionDir(packId).also { it.mkdirs() }
        val gameDir = AppPaths.versionGameDir(packId).also { it.mkdirs() }
        val modsDir = File(gameDir, "mods").also { it.mkdirs() }

        val profile = JsonObject().apply {
            addProperty("id", packId)
            addProperty("inheritsFrom", parentId)
            addProperty("type", "release")
            add("libraries", com.google.gson.JsonArray())
        }
        AppPaths.versionJson(packId).writeText(MojangApi.gson().toJson(profile))

        // 4. Mods
        val files = index.getAsJsonArray("files") ?: com.google.gson.JsonArray()
        val items = files.mapNotNull { element ->
            val item = element.asJsonObject
            val path = item.get("path")?.asString ?: return@mapNotNull null
            val downloads = item.getAsJsonArray("downloads")?.map { it.asString }.orEmpty()
            if (downloads.isEmpty()) return@mapNotNull null
            val env = item.getAsJsonObject("env")
            if (env?.get("client")?.asString == "unsupported") return@mapNotNull null
            Triple(path, downloads.first(), item.getAsJsonObject("hashes")?.get("sha1")?.asString)
        }

        items.forEachIndexed { index2, (path, url, sha1) ->
            onProgress(
                InstallProgress(
                    stage = "Downloading modpack files",
                    filesDone = index2,
                    filesTotal = items.size,
                    currentFile = path.substringAfterLast('/')
                )
            )
            val target = File(gameDir, path)
            target.parentFile?.mkdirs()
            runCatching { Net.download(url, target, sha1) }
        }

        // 5. Overrides (config, keybinds, resourcepacks…)
        ZipFile(local).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("overrides/") && !it.isDirectory }
                .forEach { entry ->
                    val relative = entry.name.removePrefix("overrides/")
                    val target = File(gameDir, relative)
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
        }
        local.delete()

        Packs.write(
            packId,
            PackMeta(
                projectId = projectId,
                slug = slug,
                name = packName,
                versionName = packVersion,
                mcVersion = mcVersion,
                loader = loaderName,
                loaderVersion = loaderVersion
            )
        )

        onProgress(InstallProgress(stage = "Modpack ready", filesDone = items.size, filesTotal = items.size))
        packId
    }
}
