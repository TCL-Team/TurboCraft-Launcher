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

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

private val GSON = Gson()

/** https://piston-meta.mojang.com/mc/game/version_manifest_v2.json */
data class VersionManifest(
    val latest: Latest = Latest("", ""),
    val versions: List<ManifestEntry> = emptyList(),
) {
    data class Latest(val release: String = "", val snapshot: String = "")
}

data class ManifestEntry(
    val id: String = "",
    val type: String = "",
    val url: String = "",
    @SerializedName("releaseTime") val releaseTime: String? = null,
)

data class OsRule(
    val name: String? = null,
    val arch: String? = null,
    val version: String? = null,
)

data class Rule(
    val action: String = "allow",
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null,
)

data class Extract(val exclude: List<String> = emptyList())

data class DownloadArtifact(
    val path: String? = null,
    val sha1: String? = null,
    val size: Long = 0,
    val url: String? = null,
)

data class LibraryDownloads(
    val artifact: DownloadArtifact? = null,
    val classifiers: Map<String, DownloadArtifact>? = null,
)

data class Library(
    val name: String = "",
    val downloads: LibraryDownloads? = null,
    val natives: Map<String, String>? = null,
    val rules: List<Rule> = emptyList(),
    val extract: Extract? = null,
    val url: String? = null,
)

data class VersionDownloads(
    val client: DownloadArtifact? = null,
    val server: DownloadArtifact? = null,
)

data class AssetIndex(
    val id: String = "",
    val sha1: String? = null,
    val size: Long = 0,
    val totalSize: Long = 0,
    val url: String? = null,
)

data class JavaVersion(val component: String? = null, val majorVersion: Int = 8)

data class VersionArguments(
    val game: List<JsonElement> = emptyList(),
    val jvm: List<JsonElement> = emptyList(),
)

/** versions/<id>/<id>.json */
data class VersionJson(
    val id: String = "",
    val type: String? = null,
    val mainClass: String? = null,
    val assets: String? = null,
    val assetIndex: AssetIndex? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val javaVersion: JavaVersion? = null,
    val minecraftArguments: String? = null,
    val arguments: VersionArguments? = null,
    val jar: String? = null,
    val inheritsFrom: String? = null,
)

object MojangApi {
    const val VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    const val RESOURCES = "https://resources.download.minecraft.net/"

    fun manifest(): VersionManifest = GSON.fromJson(Net.getString(VERSION_MANIFEST), VersionManifest::class.java)

    fun versionJson(url: String): VersionJson = GSON.fromJson(Net.getString(url), VersionJson::class.java)

    fun parse(json: String): VersionJson = GSON.fromJson(json, VersionJson::class.java)

    fun parseManifest(json: String): VersionManifest = GSON.fromJson(json, VersionManifest::class.java)

    fun gson(): Gson = GSON
}

/** Current OS name as Minecraft manifest expects it */
object OsInfo {
    val name: String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "osx"
        else -> "linux"
    }

    val arch: String = when (val a = System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        "x86", "i386", "i686" -> "x86"
        else -> a
    }

    val isWindows: Boolean get() = name == "windows"
}

/**
 * Rules evaluate karta hai — Minecraft ka standard rule engine.
 * features map abhi khaali hai (demo mode / custom resolution off).
 */
fun List<Rule>.allows(features: Map<String, Boolean> = emptyMap()): Boolean {
    if (isEmpty()) return true
    var allowed = false
    for (rule in this) {
        val osMatches = rule.os?.let { os ->
            val nameOk = os.name == null || os.name == OsInfo.name
            val archOk = os.arch == null || os.arch == OsInfo.arch
            nameOk && archOk
        } ?: true
        val featuresMatch = rule.features?.all { (key, value) -> (features[key] ?: false) == value } ?: true
        if (osMatches && featuresMatch) {
            allowed = rule.action == "allow"
        }
    }
    return allowed
}

/** Natives classifier key for this OS, e.g. "natives-windows" */
fun Library.nativeClassifier(): String? {
    val template = natives?.get(OsInfo.name) ?: return null
    return template.replace("\${arch}", if (OsInfo.arch == "x86_64") "64" else "32")
}

/** "org.ow2.asm:asm:9.7" -> "org/ow2/asm/asm/9.7/asm-9.7.jar" */
fun libraryNameToPath(name: String): String {
    val parts = name.split(":")
    if (parts.size < 3) return name
    val group = parts[0].replace('.', '/')
    val artifact = parts[1]
    val version = parts[2]
    val classifier = if (parts.size > 3) "-${parts[3]}" else ""
    return "$group/$artifact/$version/$artifact-$version$classifier.jar"
}
