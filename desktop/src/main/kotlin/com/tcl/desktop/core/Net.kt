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
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipInputStream

/**
 * HTTP layer — koi third-party library nahi, JDK ka HttpClient.
 */
object Net {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private fun request(url: String, headers: Map<String, String>): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", "TurboCraftLauncher/0.1 (desktop)")
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder
    }

    fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        val response = client.send(
            request(url, headers).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        )
        check(response.statusCode() in 200..299) {
            "HTTP ${response.statusCode()} for $url"
        }
        return response.body()
    }

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        val response = client.send(
            request(url, headers + mapOf("Content-Type" to "application/x-www-form-urlencoded"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        return response.body()
    }

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String {
        val response = client.send(
            request(url, headers + mapOf("Content-Type" to "application/json", "Accept" to "application/json"))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        )
        return response.body()
    }

    /**
     * File download (resume ke bina, simple aur reliable).
     * @param expectedSha1 diya ho to pehle check hota hai ki file puraani/sahi hai ya nahi
     */
    suspend fun download(
        url: String,
        target: File,
        expectedSha1: String? = null,
        onChunk: (Long) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (target.isFile && expectedSha1 != null && target.length() > 0 && sha1(target) == expectedSha1) {
            return@withContext
        }
        if (target.isFile && expectedSha1 == null && target.length() > 0) {
            return@withContext
        }

        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")

        val request = request(url, emptyMap()).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() in 200..299) {
            "HTTP ${response.statusCode()} for $url"
        }

        response.body().use { input ->
            tmp.outputStream().buffered(256 * 1024).use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    onChunk(read.toLong())
                }
            }
        }

        if (expectedSha1 != null) {
            val actual = sha1(tmp)
            if (!actual.equals(expectedSha1, ignoreCase = true)) {
                tmp.delete()
                error("Checksum mismatch for ${target.name}: $actual != $expectedSha1")
            }
        }

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Zip extract — [exclude] prefixes skip hote hain */
    fun unzip(input: InputStream, targetDir: File, exclude: List<String> = emptyList()) {
        targetDir.mkdirs()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (exclude.any { name.startsWith(it) }) {
                    zip.closeEntry()
                    continue
                }
                val out = File(targetDir, name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }
}
