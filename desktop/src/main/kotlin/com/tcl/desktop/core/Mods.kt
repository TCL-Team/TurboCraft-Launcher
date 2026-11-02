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
import java.util.zip.ZipFile

/** Ek mod file (`.jar` ya band kiya hua `.jar.disabled`) */
data class ModFile(
    val file: File,
    val enabled: Boolean,
) {
    val name: String get() = file.name.removeSuffix(".disabled")
    val sizeLabel: String
        get() {
            val bytes = file.length()
            return when {
                bytes > 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
                bytes > 1024 -> "${bytes / 1024} KB"
                else -> "$bytes B"
            }
        }
}

/**
 * Mods folder ka simple manager — list, enable/disable (rename), delete, open folder.
 * Mod metadata (name/version) jar ke `fabric.mod.json` / `META-INF/mods.toml` se padhta hai.
 */
object Mods {
    private const val DISABLED = ".disabled"

    val folder: File get() = File(AppPaths.game, "mods")

    fun list(): List<ModFile> {
        val dir = folder.also { it.mkdirs() }
        return dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".jar$DISABLED")) }
            ?.map { ModFile(it, !it.name.endsWith(DISABLED)) }
            ?.sortedWith(compareByDescending<ModFile> { it.enabled }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    /** Mod jar ke andar se display name / version nikalta hai (na mile to file name) */
    fun displayInfo(mod: ModFile): String {
        return runCatching {
            ZipFile(mod.file).use { zip ->
                val fabric = zip.getEntry("fabric.mod.json")
                if (fabric != null) {
                    val json = zip.getInputStream(fabric).bufferedReader().readText()
                    val obj = MojangApi.gson().fromJson(json, com.google.gson.JsonObject::class.java)
                    val name = obj.get("name")?.asString
                    val version = obj.get("version")?.asString
                    if (!name.isNullOrBlank()) return@use listOfNotNull(name, version).joinToString(" ")
                }
            }
        }.getOrNull() ?: mod.name
    }

    fun toggle(mod: ModFile): Boolean {
        val target = if (mod.enabled) {
            File(mod.file.parentFile, mod.file.name + DISABLED)
        } else {
            File(mod.file.parentFile, mod.name)
        }
        return runCatching {
            if (target.exists()) false else mod.file.renameTo(target)
        }.getOrDefault(false)
    }

    fun delete(mod: ModFile): Boolean = runCatching { mod.file.delete() }.getOrDefault(false)

    /** Mods folder me daalne ke liye — user file picker se choose karta hai */
    fun import(files: List<File>): Int {
        folder.mkdirs()
        return files.count { source ->
            runCatching { source.copyTo(File(folder, source.name), overwrite = true) }.isSuccess
        }
    }

    fun openFolder() {
        runCatching {
            folder.mkdirs()
            java.awt.Desktop.getDesktop().open(folder)
        }
    }
}
