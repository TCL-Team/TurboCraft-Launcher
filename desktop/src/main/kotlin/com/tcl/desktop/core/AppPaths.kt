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

import java.io.File
import java.nio.file.Files

/**
 * Saare launcher paths ek jagah.
 *
 * Windows par `%APPDATA%\TurboCraftLauncher`, baaki OS par `~/.turboCraft` —
 * game folder bilkul `.minecraft` jaisa layout follow karta hai.
 */
object AppPaths {
    private val home: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val appData = System.getenv("APPDATA")
        if (appData.isNullOrBlank()) File(userHome, ".turboCraft") else File(appData)
    }

    /** Launcher root (settings, accounts, java, logs) */
    val root: File = File(home, "TurboCraftLauncher")

    /** `.minecraft` equivalent */
    val game: File = File(root, "game")
    val versions: File = File(game, "versions")
    val libraries: File = File(game, "libraries")
    val assets: File = File(game, "assets")
    val assetIndexes: File = File(assets, "indexes")
    val assetObjects: File = File(assets, "objects")

    val java: File = File(root, "java")
    val logs: File = File(root, "logs")

    val settingsFile: File = File(root, "settings.json")
    val accountsFile: File = File(root, "accounts.json")

    fun ensure() {
        listOf(root, game, versions, libraries, assets, assetIndexes, assetObjects, java, logs)
            .forEach { it.mkdirs() }
    }

    fun versionDir(versionId: String): File = File(versions, versionId)
    fun versionJson(versionId: String): File = File(versionDir(versionId), "$versionId.json")
    fun versionJar(versionId: String): File = File(versionDir(versionId), "$versionId.jar")
    fun versionNatives(versionId: String): File = File(versionDir(versionId), "natives")
    fun versionLog(versionId: String): File = File(logs, "$versionId-latest.log")

    fun libraryFile(path: String): File = File(libraries, path)
    fun assetObject(hash: String): File = File(assetObjects, hash.substring(0, 2) + "/" + hash)
    fun assetIndex(id: String): File = File(assetIndexes, "$id.json")

    /** Directory me kitni jagah hai (debug ke liye) */
    fun gameSizeMb(): Long {
        if (!game.exists()) return 0
        return Files.walk(game.toPath()).use { stream ->
            stream.filter { Files.isRegularFile(it) }.mapToLong { runCatching { Files.size(it) }.getOrDefault(0L) }.sum()
        } / (1024 * 1024)
    }
}
