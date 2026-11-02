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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Install progress — UI isi ko dikhata hai.
 */
data class InstallProgress(
    val stage: String,
    val filesDone: Int = 0,
    val filesTotal: Int = 0,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val currentFile: String = "",
) {
    val fraction: Float
        get() = when {
            filesTotal > 0 -> filesDone.toFloat() / filesTotal
            bytesTotal > 0 -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
            else -> 0f
        }
}

/**
 * Vanilla Minecraft installer — version json, client jar, libraries, natives aur assets.
 * (Loader support — Fabric/NeoForge — agla step hai.)
 */
class GameInstaller {

    /** Manifest entry dhundta hai; cache bhi rakhta hai. */
    suspend fun findVersion(versionId: String): ManifestEntry = withContext(Dispatchers.IO) {
        val manifest = MojangApi.manifest()
        manifest.versions.firstOrNull { it.id == versionId }
            ?: error("Version '$versionId' Minecraft manifest me nahi mila")
    }

    suspend fun listVersions(): VersionManifest = withContext(Dispatchers.IO) { MojangApi.manifest() }

    /**
     * Poora vanilla install. Already maujood files skip hoti hain (sha1 check ke saath).
     */
    suspend fun install(
        versionId: String,
        onProgress: (InstallProgress) -> Unit = {},
    ): VersionJson = withContext(Dispatchers.IO) {
        AppPaths.ensure()

        onProgress(InstallProgress(stage = "Manifest download ho raha hai…"))
        val entry = findVersion(versionId)

        onProgress(InstallProgress(stage = "Version metadata…", filesTotal = 1))
        val versionJsonText = Net.getString(entry.url)
        val version = MojangApi.parse(versionJsonText)

        val jsonFile = AppPaths.versionJson(versionId)
        jsonFile.parentFile?.mkdirs()
        jsonFile.writeText(versionJsonText)

        // 1. Client jar
        val client = version.downloads?.client
        if (client?.url != null) {
            onProgress(InstallProgress(stage = "Minecraft client jar…", filesTotal = 1, bytesTotal = client.size))
            val target = AppPaths.versionJar(versionId)
            var done = 0L
            Net.download(client.url, target, client.sha1) { chunk ->
                done += chunk
                onProgress(
                    InstallProgress(
                        stage = "Minecraft client jar…",
                        filesDone = 0,
                        filesTotal = 1,
                        bytesDone = done,
                        bytesTotal = client.size,
                        currentFile = target.name
                    )
                )
            }
        }

        // 2. Libraries + natives
        val libs = version.libraries.filter { it.rules.allows() }
        onProgress(InstallProgress(stage = "Libraries…", filesTotal = libs.size))
        downloadAll(
            items = libs.mapNotNull { library ->
                val artifact = library.downloads?.artifact
                val path = artifact?.path ?: libraryNameToPath(library.name)
                val url = artifact?.url ?: "${library.url ?: "https://libraries.minecraft.net/"}${libraryNameToPath(library.name)}"
                DownloadItem(url = url, target = AppPaths.libraryFile(path), sha1 = artifact?.sha1, size = artifact?.size ?: 0)
            },
            stage = "Libraries…",
            onProgress = onProgress
        )

        // Natives (legacy versions)
        val nativesDir = AppPaths.versionNatives(versionId)
        nativesDir.mkdirs()
        libs.forEach { library ->
            val classifier = library.nativeClassifier() ?: return@forEach
            val artifact = library.downloads?.classifiers?.get(classifier) ?: return@forEach
            val url = artifact.url ?: return@forEach
            val zip = File(AppPaths.root, "cache/natives/${artifact.path ?: "${library.name}-$classifier.jar"}")
            Net.download(url, zip, artifact.sha1)
            zip.inputStream().use { Net.unzip(it, nativesDir, library.extract?.exclude ?: emptyList()) }
        }

        // 3. Assets
        version.assetIndex?.let { index ->
            onProgress(InstallProgress(stage = "Asset index…", filesTotal = 1))
            val indexFile = AppPaths.assetIndex(index.id)
            Net.download(index.url ?: error("Asset index url missing"), indexFile, index.sha1)

            val objects = parseAssetObjects(indexFile.readText())
            onProgress(InstallProgress(stage = "Assets…", filesTotal = objects.size))
            val done = AtomicInteger(0)
            val bytes = AtomicLong(0)
            val semaphore = Semaphore(12)
            coroutineScope {
                objects.map { (hash, size) ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val target = AppPaths.assetObject(hash)
                            Net.download(
                                url = MojangApi.RESOURCES + hash.substring(0, 2) + "/" + hash,
                                target = target,
                                expectedSha1 = hash
                            ) { chunk ->
                                bytes.addAndGet(chunk)
                                onProgress(
                                    InstallProgress(
                                        stage = "Assets…",
                                        filesDone = done.get(),
                                        filesTotal = objects.size,
                                        bytesDone = bytes.get(),
                                        bytesTotal = index.totalSize,
                                        currentFile = hash
                                    )
                                )
                            }
                            val finished = done.incrementAndGet()
                            onProgress(
                                InstallProgress(
                                    stage = "Assets…",
                                    filesDone = finished,
                                    filesTotal = objects.size,
                                    bytesDone = bytes.get(),
                                    bytesTotal = index.totalSize
                                )
                            )
                        }
                        Unit
                    }
                }.awaitAll()
            }
        }

        onProgress(InstallProgress(stage = "Ho gaya", filesDone = 1, filesTotal = 1))
        version
    }

    private data class DownloadItem(
        val url: String,
        val target: File,
        val sha1: String?,
        val size: Long,
    )

    private suspend fun downloadAll(
        items: List<DownloadItem>,
        stage: String,
        onProgress: (InstallProgress) -> Unit,
    ) = coroutineScope {
        val done = AtomicInteger(0)
        val bytes = AtomicLong(0)
        val totalBytes = items.sumOf { it.size }
        val semaphore = Semaphore(12)
        items.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching {
                        Net.download(item.url, item.target, item.sha1) { chunk ->
                            bytes.addAndGet(chunk)
                            onProgress(
                                InstallProgress(
                                    stage = stage,
                                    filesDone = done.get(),
                                    filesTotal = items.size,
                                    bytesDone = bytes.get(),
                                    bytesTotal = totalBytes,
                                    currentFile = item.target.name
                                )
                            )
                        }
                    }
                    val finished = done.incrementAndGet()
                    onProgress(
                        InstallProgress(
                            stage = stage,
                            filesDone = finished,
                            filesTotal = items.size,
                            bytesDone = bytes.get(),
                            bytesTotal = totalBytes
                        )
                    )
                }
                Unit
            }
        }.awaitAll()
    }

    /** assets index json -> hash map (hash -> size) */
    private fun parseAssetObjects(json: String): Map<String, Long> {
        val root = MojangApi.gson().fromJson(json, com.google.gson.JsonObject::class.java)
        val objects = root.getAsJsonObject("objects") ?: return emptyMap()
        return objects.entrySet().mapNotNull { (_, value) ->
            val obj = value.asJsonObject
            val hash = obj.get("hash")?.asString ?: return@mapNotNull null
            val size = obj.get("size")?.asLong ?: 0L
            hash to size
        }.toMap()
    }

    /** Local installed versions (versions/<id>/<id>.json) */
    fun installedVersions(): List<String> {
        val dir = AppPaths.versions
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && File(it, "${it.name}.json").isFile }
            ?.map { it.name }
            ?.sortedDescending()
            ?: emptyList()
    }
}
