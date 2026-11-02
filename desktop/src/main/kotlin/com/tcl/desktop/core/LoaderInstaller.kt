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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoaderEntry(val gameVersion: String, val loaderVersion: String, val stable: Boolean) {
    /** versions/ folder me jo id banega */
    val installedId: String get() = "fabric-loader-$loaderVersion-$gameVersion"
}

/**
 * Fabric loader install — loader metadata se version json bana kar vanilla ke upar
 * `inheritsFrom` ke saath rakha jaata hai. Game launch par VersionResolver dono ko
 * merge kar leta hai.
 *
 * Quilt / NeoForge bhi isi pattern par aayenge (alag endpoints ke saath).
 */
object FabricInstaller {
    private const val META = "https://meta.fabricmc.net/v2/versions/loader"

    private fun gson() = MojangApi.gson()

    /** Diye gaye Minecraft version ke liye available loader versions */
    suspend fun loaders(gameVersion: String): List<LoaderEntry> = withContext(Dispatchers.IO) {
        val json = gson().fromJson(Net.getString("$META/$gameVersion"), Array<FabricLoaderEntryJson>::class.java)
        json.mapNotNull { entry ->
            val version = entry.loader?.version ?: return@mapNotNull null
            LoaderEntry(gameVersion, version, entry.loader.stable ?: false)
        }
    }

    suspend fun latestStable(gameVersion: String): String = withContext(Dispatchers.IO) {
        val list = loaders(gameVersion)
        (list.firstOrNull { it.stable } ?: list.firstOrNull())?.loaderVersion
            ?: error("Minecraft $gameVersion ke liye Fabric loader nahi mila")
    }

    /**
     * Fabric install — naya version id return karta hai.
     * Base vanilla version pehle install hona chahiye.
     */
    suspend fun install(
        gameVersion: String,
        loaderVersion: String? = null,
        onProgress: (InstallProgress) -> Unit = {},
        installer: GameInstaller = GameInstaller(),
    ): String = withContext(Dispatchers.IO) {
        AppPaths.ensure()
        require(AppPaths.versionJson(gameVersion).isFile) {
            "Pehle Minecraft $gameVersion install karo, phir Fabric lagega"
        }

        val loader = loaderVersion ?: latestStable(gameVersion)
        val id = "fabric-loader-$loader-$gameVersion"

        onProgress(InstallProgress(stage = "Fabric $loader profile…", filesTotal = 1))
        val profileJson = Net.getString("$META/$gameVersion/$loader/profile/json")

        val dir = AppPaths.versionDir(id)
        dir.mkdirs()
        val jsonFile = AppPaths.versionJson(id)
        jsonFile.writeText(profileJson)

        val profile = MojangApi.parse(profileJson)
        onProgress(InstallProgress(stage = "Fabric libraries…", filesTotal = profile.libraries.size))
        installer.installLibrariesOf(profile, id, "Fabric libraries…", onProgress)

        onProgress(InstallProgress(stage = "Ho gaya", filesDone = 1, filesTotal = 1))
        id
    }

    private data class FabricLoaderEntryJson(val loader: FabricLoaderJson? = null)
    private data class FabricLoaderJson(val version: String? = null, val stable: Boolean? = null)
}
