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
import java.io.File
import java.util.concurrent.TimeUnit

data class JavaRuntime(
    val javaExe: File,
    val majorVersion: Int,
    val source: String,
)

/**
 * Java runtime dhundta hai, aur na mile to Adoptium (Temurin) se download karta hai.
 * Android launcher me unpacked JRE hota tha; PC par system Java + auto-download.
 */
object JavaManager {
    private val TEMURIN =
        "https://api.adoptium.net/v3/binary/latest/%d/ga/windows/x64/jdk/hotspot/normal/eclipse"

    /** MC version ke hisaab se required Java major */
    fun requiredMajor(mcVersion: String, manifestMajor: Int? = null): Int {
        if (manifestMajor != null && manifestMajor > 0) return manifestMajor
        val parts = mcVersion.split(".", "-", "+").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return 21
        val major = parts[0]
        val minor = parts.getOrElse(1) { 0 }
        return when {
            major >= 2 -> 21              // 26.x / naye snapshots
            major == 1 && minor >= 20 -> 21
            major == 1 && minor >= 18 -> 17
            else -> 8
        }
    }

    fun detect(javaExe: File, source: String): JavaRuntime? {
        if (!javaExe.isFile) return null
        return runCatching {
            val process = ProcessBuilder(javaExe.absolutePath, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(15, TimeUnit.SECONDS)
            val major = parseMajor(output) ?: return null
            JavaRuntime(javaExe, major, source)
        }.getOrNull()
    }

    private fun parseMajor(output: String): Int? {
        val match = Regex("version \"([^\"]+)\"").find(output) ?: return null
        val version = match.groupValues[1]
        return if (version.startsWith("1.")) {
            version.removePrefix("1.").substringBefore('.').toIntOrNull()
        } else {
            version.substringBefore('.').substringBefore('-').toIntOrNull()
        }
    }

    /** System me installed saare Java */
    fun detectAll(): List<JavaRuntime> {
        val candidates = mutableListOf<Pair<File, String>>()

        System.getenv("JAVA_HOME")?.let { candidates += File(it, "bin/java.exe") to "JAVA_HOME" }

        if (!OsInfo.isWindows) {
            candidates += File("/usr/bin/java") to "system"
            candidates += File("/usr/local/bin/java") to "system"
        } else {
            val roots = listOf(
                "C:\\Program Files\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft",
                "C:\\Program Files\\Zulu",
                "C:\\Program Files\\Amazon Corretto",
                "C:\\Program Files\\BellSoft",
            )
            roots.forEach { root ->
                File(root).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                    candidates += File(dir, "bin/java.exe") to dir.name
                }
            }
            runCatching {
                val process = ProcessBuilder("where", "java").redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor(5, TimeUnit.SECONDS)
                output.lines().map { it.trim() }.filter { it.endsWith("java.exe") }
                    .forEach { candidates += File(it) to "PATH" }
            }
        }

        AppPaths.java.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            candidates += File(dir, "bin/java.exe") to "downloaded"
        }

        return candidates.mapNotNull { (file, source) -> detect(file, source) }
            .distinctBy { it.javaExe.absolutePath }
            .sortedByDescending { it.majorVersion }
    }

    /**
     * Diye gaye version ke liye suitable Java; na mile to Temurin download karta hai.
     */
    suspend fun ensure(
        mcVersion: String,
        manifestMajor: Int?,
        customPath: String,
        onLog: (String) -> Unit,
    ): JavaRuntime = withContext(Dispatchers.IO) {
        val required = requiredMajor(mcVersion, manifestMajor)

        if (customPath.isNotBlank()) {
            val custom = File(customPath)
            val exe = if (custom.isDirectory) File(custom, "bin/java.exe") else custom
            detect(exe, "custom")?.let {
                onLog("Java: custom runtime ${it.javaExe} (${it.majorVersion})")
                return@withContext it
            }
            onLog("Custom Java path kaam nahi kar raha, auto-detect par ja raha hoon.")
        }

        val installed = detectAll()
        installed.firstOrNull { it.majorVersion == required }?.let {
            onLog("Java: ${it.source} se mila ${it.majorVersion}")
            return@withContext it
        }
        installed.firstOrNull { it.majorVersion > required }?.let {
            onLog("Java: ${it.majorVersion} mila (chahiye $required), isi ko use kar raha hoon")
            return@withContext it
        }

        onLog("Java $required nahi mila — Temurin download ho raha hai…")
        downloadTemurin(required, onLog)
    }

    private suspend fun downloadTemurin(major: Int, onLog: (String) -> Unit): JavaRuntime {
        val target = File(AppPaths.java, "jdk-$major")
        AppPaths.java.mkdirs()

        val existing = File(target, "bin/java.exe")
        if (existing.isFile) {
            detect(existing, "downloaded")?.let { return it }
        }

        val zip = File(AppPaths.java, "jdk-$major.zip")
        if (zip.isFile) zip.delete()
        onLog("Temurin $major download: ${TEMURIN.format(major)}")
        Net.download(TEMURIN.format(major), zip)

        onLog("Java extract ho raha hai…")
        zip.inputStream().use { Net.unzip(it, target) }
        zip.delete()

        // Zip ke andar ek top-level folder hota hai (jdk-21.0.x+y)
        val nested = target.listFiles()?.firstOrNull { it.isDirectory && File(it, "bin/java.exe").isFile }
        if (nested != null) {
            nested.listFiles()?.forEach { file -> file.renameTo(File(target, file.name)) }
            nested.deleteRecursively()
        }

        val exe = File(target, "bin/java.exe")
        return detect(exe, "downloaded") ?: error("Temurin $major extract hone ke baad bhi Java nahi mila")
    }
}
