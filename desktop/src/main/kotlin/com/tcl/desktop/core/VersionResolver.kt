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

import com.google.gson.JsonElement

/**
 * version json + uske parents (inheritsFrom) ko merge karke ek "final" version.
 *
 * Fabric / Quilt / NeoForge aise hi kaam karte hain: child json me sirf loader ka
 * mainClass, libraries aur kuch arguments hote hain, baaki vanilla se aata hai.
 */
data class ResolvedVersion(
    val id: String,
    /** jis folder se client jar aayegi (chain ka root vanilla version) */
    val jarId: String,
    val mainClass: String,
    val assetIndex: String?,
    val libraries: List<Library>,
    val gameArguments: List<JsonElement>,
    val legacyArguments: String?,
    val jvmArguments: List<JsonElement>,
    val javaMajor: Int?,
    val type: String,
) {
    fun classpath(): String {
        val entries = mutableListOf<String>()
        AppPaths.versionJar(jarId).takeIf { it.isFile }?.let { entries += it.absolutePath }
        libraries.filter { it.rules.allows() }.forEach { library ->
            val path = library.downloads?.artifact?.path ?: libraryNameToPath(library.name)
            val file = AppPaths.libraryFile(path)
            if (file.isFile) entries += file.absolutePath
        }
        return entries.joinToString(java.io.File.pathSeparator)
    }
}

object VersionResolver {

    fun resolve(versionId: String): ResolvedVersion {
        val version = readJson(versionId)
        return resolve(versionId, version, 0)
    }

    /** Bina disk se padhe (tests / already parsed json) */
    fun fromJson(versionId: String, version: VersionJson): ResolvedVersion = resolve(versionId, version, 0)

    private fun readJson(versionId: String): VersionJson {
        val file = AppPaths.versionJson(versionId)
        require(file.isFile) { "Version '$versionId' install nahi hai" }
        return MojangApi.parse(file.readText())
    }

    private fun resolve(versionId: String, version: VersionJson, depth: Int): ResolvedVersion {
        require(depth < 8) { "inheritsFrom chain bahut lambi hai ($versionId)" }

        val parentId = version.inheritsFrom
        if (parentId.isNullOrBlank()) {
            return ResolvedVersion(
                id = versionId,
                jarId = version.jar ?: versionId,
                mainClass = version.mainClass ?: "net.minecraft.client.main.Main",
                assetIndex = version.assetIndex?.id ?: version.assets,
                libraries = version.libraries,
                gameArguments = version.arguments?.game.orEmpty(),
                legacyArguments = version.minecraftArguments,
                jvmArguments = version.arguments?.jvm.orEmpty(),
                javaMajor = version.javaVersion?.majorVersion,
                type = version.type ?: "release",
            )
        }

        val parent = resolve(parentId, readJson(parentId), depth + 1)
        return merge(versionId, version, parent)
    }

    /** Child json ko resolved parent ke upar chadhaata hai (loader profiles ke liye) */
    fun merge(versionId: String, child: VersionJson, parent: ResolvedVersion): ResolvedVersion =
        ResolvedVersion(
            id = versionId,
            jarId = parent.jarId,
            mainClass = child.mainClass ?: parent.mainClass,
            assetIndex = child.assetIndex?.id ?: child.assets ?: parent.assetIndex,
            // child pehle — loader apni libraries override kar sakta hai
            libraries = (child.libraries + parent.libraries).distinctBy { it.name },
            gameArguments = parent.gameArguments + child.arguments?.game.orEmpty(),
            legacyArguments = child.minecraftArguments ?: parent.legacyArguments,
            jvmArguments = parent.jvmArguments + child.arguments?.jvm.orEmpty(),
            javaMajor = child.javaVersion?.majorVersion ?: parent.javaMajor,
            type = child.type ?: parent.type,
        )

    /** Parent chain ke saare ids (khud + ancestors) */
    fun chain(versionId: String): List<String> {
        val result = mutableListOf<String>()
        var current: String? = versionId
        while (!current.isNullOrBlank() && result.size < 8) {
            result += current
            current = runCatching { readJson(current).inheritsFrom }.getOrNull()
        }
        return result
    }
}
