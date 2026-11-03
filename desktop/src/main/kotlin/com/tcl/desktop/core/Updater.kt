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

import com.google.gson.JsonObject
import com.tcl.desktop.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UpdateInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String?,
    val isNewer: Boolean,
)

/**
 * Mobile edition ka update checker — GitHub releases se latest `desktop-v*` tag.
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/TCL-Team/TurboCraft-Launcher/releases?per_page=20"

    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val body = Net.getString(
            RELEASES,
            mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to Brand.USER_AGENT,
            )
        )
        val releases = MojangApi.gson().fromJson(body, Array<JsonObject>::class.java)
        val release = releases.firstOrNull { it.get("tag_name")?.asString?.startsWith("desktop-v") == true }
            ?: releases.firstOrNull()
            ?: return@withContext null

        val tag = release.get("tag_name")?.asString ?: return@withContext null
        val version = tag.removePrefix("desktop-v").removePrefix("v")
        val assets = release.getAsJsonArray("assets")
        val msi = assets?.map { it.asJsonObject }
            ?.firstOrNull { it.get("name")?.asString?.endsWith(".msi") == true }
            ?.get("browser_download_url")?.asString

        UpdateInfo(
            tag = tag,
            version = version,
            notes = release.get("body")?.asString.orEmpty(),
            pageUrl = release.get("html_url")?.asString ?: Brand.HOME_URL,
            downloadUrl = msi,
            isNewer = isNewer(version, Brand.VERSION_NAME),
        )
    }

    /** "0.5.0" > "0.4.0" */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(value: String) = value.split(".", "-", "+")
            .mapNotNull { it.toIntOrNull() }
            .let { list -> list + List(4 - list.size) { 0 } }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
