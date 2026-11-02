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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder

data class ProjectHit(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val author: String,
    val downloads: Long,
    val iconUrl: String?,
    val projectType: String,
    val categories: List<String>,
)

data class ProjectFile(
    val url: String,
    val filename: String,
    val sha1: String?,
    val size: Long,
    val primary: Boolean,
)

data class ProjectVersion(
    val id: String,
    val name: String,
    val versionNumber: String,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val dependencies: List<Dependency>,
    val files: List<ProjectFile>,
) {
    val primaryFile: ProjectFile? get() = files.firstOrNull { it.primary } ?: files.firstOrNull()
}

data class Dependency(val projectId: String?, val versionId: String?, val type: String)

/**
 * Modrinth API — mods, modpacks, resource packs.
 * (CurseForge ki API key chahiye hoti hai, isliye Modrinth default hai.)
 */
object Modrinth {
    private const val BASE = "https://api.modrinth.com/v2"
    private const val CDN = "https://cdn.modrinth.com/data"

    private fun gson() = MojangApi.gson()

    private fun quote(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""

    /**
     * Search — facets Modrinth syntax me: [[ "project_type:mod" ], [ "versions:1.21.1" ]]
     */
    suspend fun search(
        query: String,
        projectType: String,          // mod | modpack | resourcepack | shader
        gameVersion: String? = null,
        loader: String? = null,
        limit: Int = 20,
    ): List<ProjectHit> = withContext(Dispatchers.IO) {
        // Modrinth facets = JSON array of arrays. Har group AND hota hai,
        // group ke andar ke options OR. (Pehle galat JSON banti thi → HTTP 400.)
        val groups = mutableListOf<String>()
        groups += "[${quote("project_type:$projectType")}]"
        if (!gameVersion.isNullOrBlank()) groups += "[${quote("versions:$gameVersion")}]"
        if (!loader.isNullOrBlank()) groups += "[${quote("categories:$loader")}]"
        val facets = "[" + groups.joinToString(",") + "]"

        val url = buildString {
            append("$BASE/search?query=").append(URLEncoder.encode(query, Charsets.UTF_8))
            append("&limit=").append(limit)
            append("&index=").append(if (query.isBlank()) "downloads" else "relevance")
            append("&facets=").append(URLEncoder.encode(facets, Charsets.UTF_8))
        }
        val json = gson().fromJson(Net.getString(url), JsonObject::class.java)
        val hits = json.getAsJsonArray("hits") ?: JsonArray()
        hits.mapNotNull { element ->
            val hit = element.asJsonObject
            ProjectHit(
                id = hit.get("project_id")?.asString ?: return@mapNotNull null,
                slug = hit.get("slug")?.asString ?: "",
                title = hit.get("title")?.asString ?: "Unknown",
                description = hit.get("description")?.asString ?: "",
                author = hit.get("author")?.asString ?: "",
                downloads = hit.get("downloads")?.asLong ?: 0,
                iconUrl = hit.get("icon_url")?.asString,
                projectType = hit.get("project_type")?.asString ?: projectType,
                categories = hit.getAsJsonArray("categories")?.map { it.asString }.orEmpty(),
            )
        }
    }

    /** Kisi project ke versions (loader + mc filter ke saath) */
    suspend fun versions(
        projectId: String,
        gameVersion: String? = null,
        loader: String? = null,
    ): List<ProjectVersion> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$BASE/project/").append(projectId).append("/version")
            val params = mutableListOf<String>()
            if (!gameVersion.isNullOrBlank()) params += "game_versions=" + URLEncoder.encode("[\"$gameVersion\"]", Charsets.UTF_8)
            if (!loader.isNullOrBlank()) params += "loaders=" + URLEncoder.encode("[\"$loader\"]", Charsets.UTF_8)
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
        val array = gson().fromJson(Net.getString(url), JsonArray::class.java)
        array.mapNotNull { element -> parseVersion(element.asJsonObject) }
    }

    suspend fun versionById(versionId: String): ProjectVersion? = withContext(Dispatchers.IO) {
        runCatching {
            parseVersion(gson().fromJson(Net.getString("$BASE/version/$versionId"), JsonObject::class.java))
        }.getOrNull()
    }

    private fun parseVersion(json: JsonObject): ProjectVersion? {
        val files = json.getAsJsonArray("files")?.mapNotNull { element ->
            val file = element.asJsonObject
            val url = file.get("url")?.asString ?: return@mapNotNull null
            ProjectFile(
                url = url,
                filename = file.get("filename")?.asString ?: url.substringAfterLast('/'),
                sha1 = file.getAsJsonObject("hashes")?.get("sha1")?.asString,
                size = file.get("size")?.asLong ?: 0,
                primary = file.get("primary")?.asBoolean ?: false,
            )
        }.orEmpty()
        val deps = json.getAsJsonArray("dependencies")?.mapNotNull { element ->
            val dep = element.asJsonObject
            Dependency(
                projectId = dep.get("project_id")?.asString,
                versionId = dep.get("version_id")?.asString,
                type = dep.get("dependency_type")?.asString ?: "required",
            )
        }.orEmpty()

        return ProjectVersion(
            id = json.get("id")?.asString ?: return null,
            name = json.get("name")?.asString ?: "",
            versionNumber = json.get("version_number")?.asString ?: "",
            gameVersions = json.getAsJsonArray("game_versions")?.map { it.asString }.orEmpty(),
            loaders = json.getAsJsonArray("loaders")?.map { it.asString }.orEmpty(),
            dependencies = deps,
            files = files,
        )
    }

    /**
     * Mod install — primary file + required dependencies (depth 3 tak).
     * @return kitni files install hui
     */
    suspend fun installMod(
        version: ProjectVersion,
        targetModsDir: File,
        gameVersion: String?,
        loader: String?,
        onStatus: (String) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        targetModsDir.mkdirs()
        var installed = 0
        val seen = mutableSetOf<String>()

        suspend fun installOne(projectVersion: ProjectVersion, depth: Int) {
            val file = projectVersion.primaryFile ?: return
            if (!seen.add(file.filename)) return
            onStatus("Downloading ${file.filename}")
            Net.download(file.url, File(targetModsDir, file.filename), file.sha1)
            installed++

            if (depth >= 3) return
            projectVersion.dependencies
                .filter { it.type == "required" }
                .forEach { dependency ->
                    val depVersion = dependency.versionId?.let { versionById(it) }
                        ?: dependency.projectId?.let { projectId ->
                            versions(projectId, gameVersion, loader).firstOrNull()
                        }
                    if (depVersion != null) installOne(depVersion, depth + 1)
                }
        }

        installOne(version, 0)
        installed
    }

    /** Project icon ko cache me laata hai (UI ke liye) */
    suspend fun iconFile(iconUrl: String?, projectId: String): File? = withContext(Dispatchers.IO) {
        if (iconUrl.isNullOrBlank()) return@withContext null
        val cache = File(AppPaths.root, "cache/icons").also { it.mkdirs() }
        val target = File(cache, "$projectId.png")
        if (target.isFile && target.length() > 0) return@withContext target
        runCatching { Net.download(iconUrl, target) }.getOrNull()?.let { target }
    }

    /** Version list URL se seedha file (modpack file ke liye) */
    fun cdnFileUrl(projectId: String, versionId: String, filename: String) =
        "$CDN/$projectId/versions/$versionId/$filename"
}

/** Resource pack / shader install — file seedha game folder me (no loader). */
object ContentInstaller {

    private fun targetDir(kind: String, versionId: String) = when (kind) {
        "shader" -> java.io.File(AppPaths.gameDirFor(versionId), "shaderpacks")
        else -> java.io.File(AppPaths.gameDirFor(versionId), "resourcepacks")
    }

    suspend fun install(
        kind: String,
        hit: ProjectHit,
        versionId: String,
        gameVersion: String?,
        onStatus: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val versions = Modrinth.versions(hit.id, gameVersion, null)
        val chosen = versions.firstOrNull() ?: Modrinth.versions(hit.id).firstOrNull()
            ?: error("No downloadable file for ${hit.title}")
        val file = chosen.primaryFile ?: error("No file in that version")
        val dir = targetDir(kind, versionId).also { it.mkdirs() }
        onStatus("Downloading ${file.filename}")
        Net.download(file.url, File(dir, file.filename), file.sha1)
        file.filename
    }

    fun list(kind: String, versionId: String): List<File> =
        targetDir(kind, versionId).listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun delete(kind: String, versionId: String, file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    fun openFolder(kind: String, versionId: String) {
        runCatching {
            val dir = targetDir(kind, versionId).also { it.mkdirs() }
            java.awt.Desktop.getDesktop().open(dir)
        }
    }
}
