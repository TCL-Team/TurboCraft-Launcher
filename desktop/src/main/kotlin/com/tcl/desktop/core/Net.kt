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

import com.tcl.desktop.Brand
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

/** User ko dikhane layak network error (stack trace nahi) */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * HTTP layer — koi third-party library nahi, JDK ka HttpClient.
 *
 * Client lazy banaya hai: agar runtime me `java.net.http` module na ho to
 * error saaf message deta hai (pehle "Could not initialize class Net" aata tha).
 */
object Net {
    private val client: HttpClient by lazy {
        try {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        } catch (error: Throwable) {
            throw NetworkException(
                "This build is missing the Java HTTP module (java.net.http). " +
                    "Please download the latest launcher build.",
                error
            )
        }
    }

    private fun request(url: String, headers: Map<String, String>): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", Brand.USER_AGENT)
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder
    }

    /** Ek friendly message — kya galat hua, user kya kare */
    private fun enrich(url: String, error: Throwable): Nothing {
        val host = runCatching { URI.create(url).host }.getOrNull() ?: url
        val message = when {
            error is NetworkException -> error.message ?: "Network error"
            error is java.net.UnknownHostException ->
                "Can't reach $host — no internet connection or DNS is blocked."
            error is java.net.ConnectException || error is java.net.http.HttpConnectTimeoutException ->
                "Can't connect to $host — check your internet connection or firewall."
            error is java.net.http.HttpTimeoutException ->
                "$host took too long to respond. Try again."
            error is javax.net.ssl.SSLException ->
                "Secure connection to $host failed. Check your system date/time and try again."
            else -> "$host: ${error.message ?: error.javaClass.simpleName}"
        }
        throw NetworkException(message, error)
    }

    fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        val response = try {
            client.send(request(url, headers).GET().build(), HttpResponse.BodyHandlers.ofString())
        } catch (error: Throwable) {
            enrich(url, error)
        }
        check(response.statusCode() in 200..299) {
            "Server returned HTTP ${response.statusCode()} for ${runCatching { URI.create(url).host }.getOrElse { url }}"
        }
        return response.body()
    }

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        val response = try {
            client.send(
                request(url, headers + mapOf("Content-Type" to "application/x-www-form-urlencoded"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
        } catch (error: Throwable) {
            enrich(url, error)
        }
        return response.body()
    }

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap()): String {
        val response = try {
            client.send(
                request(url, headers + mapOf("Content-Type" to "application/json", "Accept" to "application/json"))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            )
        } catch (error: Throwable) {
            enrich(url, error)
        }
        return response.body()
    }

    /** Quick connectivity check (launcher start par) */
    suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
        runCatching { getString(MojangApi.VERSION_MANIFEST).isNotBlank() }.getOrDefault(false)
    }

    /**
     * File download (no resume, simple + reliable).
     * @param expectedSha1 agar diya hai to pehle check hota hai ki file sahi hai ya nahi
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

        val response = try {
            client.send(request(url, emptyMap()).GET().build(), HttpResponse.BodyHandlers.ofInputStream())
        } catch (error: Throwable) {
            enrich(url, error)
        }
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
