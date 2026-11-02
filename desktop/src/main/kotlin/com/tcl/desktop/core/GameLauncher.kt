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
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LaunchResult(val process: Process, val command: List<String>)

/**
 * Game process banata hai (PC par ye Android ke JNI/EGL layer ka replacement hai:
 * bas `java -cp ... net.minecraft.client.main.Main`).
 */
object GameLauncher {

    fun classpath(versionId: String, version: VersionJson): String {
        val entries = mutableListOf<String>()
        AppPaths.versionJar(versionId).takeIf { it.isFile }?.let { entries += it.absolutePath }

        version.libraries.filter { it.rules.allows() }.forEach { library ->
            val path = library.downloads?.artifact?.path ?: libraryNameToPath(library.name)
            val file = AppPaths.libraryFile(path)
            if (file.isFile) entries += file.absolutePath
        }
        return entries.joinToString(File.pathSeparator)
    }

    fun buildCommand(
        versionId: String,
        version: VersionJson,
        account: Account,
        settings: Settings,
        java: JavaRuntime,
    ): List<String> {
        val nativesDir = AppPaths.versionNatives(versionId).also { it.mkdirs() }
        val placeholders = mapOf(
            "auth_player_name" to account.name,
            "version_name" to versionId,
            "game_directory" to AppPaths.game.absolutePath,
            "assets_root" to AppPaths.assets.absolutePath,
            "game_assets" to File(AppPaths.assets, "virtual/legacy").absolutePath,
            "assets_index_name" to (version.assets ?: version.assetIndex?.id ?: "legacy"),
            "auth_uuid" to account.uuid.replace("-", ""),
            "auth_access_token" to account.accessToken,
            "auth_session" to "token:${account.accessToken}:${account.uuid}",
            "user_type" to if (account.type == AccountType.MICROSOFT) "msa" else "legacy",
            "version_type" to (version.type ?: "release"),
            "natives_directory" to nativesDir.absolutePath,
            "launcher_name" to "TurboCraftLauncher",
            "launcher_version" to "0.1.0",
            "classpath" to classpath(versionId, version),
            "classpath_separator" to File.pathSeparator,
            "library_directory" to AppPaths.libraries.absolutePath,
            "user_properties" to "{}",
            "resolution_width" to settings.windowWidth.toString(),
            "resolution_height" to settings.windowHeight.toString(),
            "clientid" to "",
            "auth_xuid" to "",
        )

        val command = mutableListOf(java.javaExe.absolutePath)

        // Memory + user JVM args
        command += "-Xmx${settings.memoryMb}M"
        command += "-Xms${(settings.memoryMb / 2).coerceAtLeast(512)}M"
        if (settings.jvmArgs.isNotBlank()) {
            splitArgs(settings.jvmArgs).forEach { command += it }
        }

        val jvmArgs = version.arguments?.jvm.orEmpty()
        if (jvmArgs.isNotEmpty()) {
            resolveArguments(jvmArgs, placeholders).forEach { command += it }
        } else {
            command += "-Djava.library.path=${nativesDir.absolutePath}"
            command += "-cp"
            command += classpath(versionId, version)
        }

        command += version.mainClass ?: "net.minecraft.client.main.Main"

        val gameArgs: List<String> = if (version.arguments?.game?.isNotEmpty() == true) {
            resolveArguments(version.arguments.game, placeholders)
        } else {
            splitArgs(version.minecraftArguments ?: "").map { replacePlaceholders(it, placeholders) }
        }
        command += gameArgs

        // Window size (agar game args me nahi hai)
        if (gameArgs.none { it == "--width" } && settings.windowWidth > 0) {
            command += listOf("--width", settings.windowWidth.toString())
            command += listOf("--height", settings.windowHeight.toString())
        }

        return command
    }

    fun resolveArguments(elements: List<JsonElement>, placeholders: Map<String, String>): List<String> {
        val result = mutableListOf<String>()
        elements.forEach { element ->
            when {
                element.isJsonPrimitive -> result += replacePlaceholders(element.asString, placeholders)
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val rules = obj.get("rules")?.let {
                        MojangApi.gson().fromJson(it, Array<Rule>::class.java).toList()
                    }.orEmpty()
                    // demo mode / custom resolution features off hain
                    if (!rules.allows(emptyMap())) return@forEach
                    obj.get("value")?.let { result += replacePlaceholders(it.asString, placeholders) }
                    obj.getAsJsonArray("values")?.forEach { value ->
                        result += replacePlaceholders(value.asString, placeholders)
                    }
                }
            }
        }
        return result
    }

    fun replacePlaceholders(value: String, placeholders: Map<String, String>): String {
        var result = value
        placeholders.forEach { (key, replacement) -> result = result.replace("\${$key}", replacement) }
        return result
    }

    /** Simple shell-like split (quotes supported) */
    fun splitArgs(line: String): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        line.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                char.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        args += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) args += current.toString()
        return args
    }

    suspend fun launch(
        versionId: String,
        account: Account,
        settings: Settings,
        onLog: (String) -> Unit,
        onExit: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        AppPaths.ensure()
        val jsonFile = AppPaths.versionJson(versionId)
        require(jsonFile.isFile) { "Version '$versionId' install nahi hai" }
        val version = MojangApi.parse(jsonFile.readText())

        onLog("Java runtime check…")
        val java = JavaManager.ensure(
            mcVersion = version.id.ifBlank { versionId },
            manifestMajor = version.javaVersion?.majorVersion,
            customPath = settings.javaPath,
            onLog = onLog
        )

        val a = if (account.type == AccountType.MICROSOFT && account.isExpired) {
            onLog("Microsoft token refresh…")
            MicrosoftAuth.refresh(account)
        } else account

        val command = buildCommand(versionId, version, a, settings, java)
        onLog("Command: " + command.joinToString(" "))

        val logFile = AppPaths.versionLog(versionId)
        logFile.parentFile?.mkdirs()

        val process = ProcessBuilder(command)
            .directory(AppPaths.game)
            .redirectErrorStream(true)
            .start()

        Thread {
            runCatching {
                logFile.bufferedWriter().use { writer ->
                    process.inputStream.bufferedReader().forEachLine { line ->
                        writer.write(line)
                        writer.newLine()
                        writer.flush()
                        onLog(line)
                    }
                }
            }
        }.apply { isDaemon = true }.start()

        onLog("Minecraft start ho gaya (pid ${process.pid()})")
        LaunchResult(process, command)
    }
}
