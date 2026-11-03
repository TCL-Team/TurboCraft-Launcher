/*
 * TurboCraft Launcher — desktop (Windows) edition
 * Copyright (C) 2025-2026 TCL Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.tcl.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.core.AppPaths
import com.tcl.desktop.core.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Project icons (Modrinth CDN) — cache me download, phir decode.
 * Compose Desktop me built-in network image loader nahi hota, isliye chhota cache.
 */
object IconCache {
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 96
    }

    private fun cacheFile(key: String): File =
        File(AppPaths.root, "cache/icons/${key.replace(Regex("[^A-Za-z0-9._-]"), "_")}.img")

    suspend fun load(url: String, key: String = url): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(memory) { memory[key] }?.let { return@withContext it }

        val file = cacheFile(key)
        if (!file.isFile || file.length() == 0L) {
            runCatching { Net.download(url, file) }.onFailure { return@withContext null }
        }
        val bitmap = runCatching { file.inputStream().use { loadImageBitmap(it) } }.getOrNull()
        if (bitmap != null) synchronized(memory) { memory[key] = bitmap }
        bitmap
    }

    fun peek(key: String): ImageBitmap? = synchronized(memory) { memory[key] }
}

@Composable
fun RemoteIcon(
    url: String?,
    key: String,
    fallbackText: String,
    size: Dp = 40.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
) {
    val bitmap by produceState(initialValue = IconCache.peek(key), key1 = url, key2 = key) {
        if (url.isNullOrBlank()) return@produceState
        value = IconCache.load(url, key)
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape)
            )
        } else {
            Text(
                text = fallbackText.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.4f).sp
            )
        }
    }
}
