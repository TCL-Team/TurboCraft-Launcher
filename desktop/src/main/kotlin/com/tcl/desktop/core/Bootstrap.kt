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

data class BootstrapStep(
    val label: String,
    val detail: String = "",
    val done: Boolean = false,
    val failed: Boolean = false,
) {
    val status: String get() = when {
        failed -> "failed"
        done -> "done"
        else -> "…"
    }
}

/**
 * Launcher start par wahi hota hai jo Android app karti hai: pehle zaroori
 * components download/prepare, phir UI. UI block nahi hota — steps emit hote hain.
 */
object Bootstrap {

    /**
     * @param onStep har step ke update par (index, progress)
     */
    suspend fun run(
        javaMajor: Int = 21,
        onStep: (List<BootstrapStep>) -> Unit,
    ): List<BootstrapStep> = withContext(Dispatchers.IO) {
        val steps = mutableListOf(
            BootstrapStep("Preparing folders"),
            BootstrapStep("Checking Java runtime"),
            BootstrapStep("Fetching Minecraft versions"),
            BootstrapStep("Checking Fabric loader"),
            BootstrapStep("Loading installed versions"),
        )

        fun emit() = onStep(steps.toList())
        /** step ko "running" + detail ke saath update */
        fun update(index: Int, detail: String = "", done: Boolean = false, failed: Boolean = false) {
            steps[index] = steps[index].copy(detail = detail, done = done, failed = failed)
            emit()
        }

        emit()

        // 1. Folders
        update(0)
        AppPaths.ensure()
        update(0, AppPaths.root.absolutePath, done = true)

        // 2. Java runtime (Temurin auto download)
        update(1)
        runCatching {
            val java = JavaManager.ensure(
                mcVersion = "bootstrap",
                manifestMajor = javaMajor,
                customPath = "",
                onLog = { line -> update(1, line) }
            )
            update(1, "${java.javaExe.absolutePath} (Java ${java.majorVersion})", done = true)
        }.onFailure { error ->
            update(1, error.message ?: "Java setup failed", failed = true)
        }

        // 3. Minecraft version list
        update(2)
        runCatching {
            val manifest = MojangApi.manifest()
            update(2, "${manifest.versions.size} versions", done = true)
        }.onFailure { error ->
            update(2, error.message ?: "No connection", failed = true)
        }

        // 4. Fabric meta (loader availability)
        update(3)
        runCatching {
            val latest = Net.getString("https://meta.fabricmc.net/v2/versions/loader")
                .let { body ->
                    Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                }
            update(3, latest?.let { "latest listed · $it" } ?: "available", done = true)
        }.onFailure {
            update(3, "skipped", failed = true)
        }

        // 5. Installed versions scan
        update(4)
        val installed = GameInstaller().installedVersions()
        update(4, if (installed.isEmpty()) "nothing installed yet" else "${installed.size} found", done = true)

        steps.toList()
    }
}
