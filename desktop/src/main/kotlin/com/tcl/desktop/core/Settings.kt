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

import com.google.gson.GsonBuilder
import java.io.File

private val GSON = GsonBuilder().setPrettyPrinting().create()

/**
 * Launcher settings — TCL Android ke `AllSettings` ka desktop equivalent.
 * Sirf wahi cheezein jo PC par matlab rakhti hain.
 */
data class Settings(
    /** Custom Java path; khaali = auto detect / auto download */
    var javaPath: String = "",
    /** Per-instance RAM (MB) */
    var memoryMb: Int = 3072,
    /** Selected version id (versions/<id>) */
    var selectedVersion: String = "",
    /** Selected account uuid */
    var selectedAccount: String = "",
    /** Launcher theme: "dark" / "light" */
    var theme: String = "dark",
    /** Game window width/height */
    var windowWidth: Int = 1280,
    var windowHeight: Int = 720,
    /** Extra JVM arguments */
    var jvmArgs: String = "",
    /** Launcher band hone par game process band karna hai? */
    var quitGameOnClose: Boolean = false,
    /** Microsoft app (client) id — khaali = launcher ka default */
    var msClientId: String = "",
    /** Discord Rich Presence */
    var discordPresence: Boolean = true,
)

object SettingsStore {
    fun load(): Settings {
        AppPaths.ensure()
        val file = AppPaths.settingsFile
        if (!file.isFile) return Settings().also { save(it) }
        return runCatching {
            GSON.fromJson(file.readText(), Settings::class.java) ?: Settings()
        }.getOrElse { Settings() }
    }

    fun save(settings: Settings) {
        runCatching {
            AppPaths.root.mkdirs()
            val tmp = File(AppPaths.root, "settings.json.tmp")
            tmp.writeText(GSON.toJson(settings))
            if (!tmp.renameTo(AppPaths.settingsFile)) {
                tmp.copyTo(AppPaths.settingsFile, overwrite = true)
                tmp.delete()
            }
        }
    }
}
