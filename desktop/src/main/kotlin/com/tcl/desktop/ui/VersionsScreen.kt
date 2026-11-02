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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.core.AppState

@Composable
fun VersionsScreen(state: AppState, focusDownload: Boolean = false) {
    var filter by remember { mutableStateOf(if (focusDownload) "" else "") }
    var typeFilter by remember { mutableStateOf("release") }
    val query = filter.ifBlank { state.versionFilter }

    val visible = state.remoteVersions
        .filter { entry ->
            val typeOk = when (typeFilter) {
                "all" -> true
                "snapshot" -> entry.type == "snapshot"
                "old" -> entry.type == "old_beta" || entry.type == "old_alpha"
                else -> entry.type == "release"
            }
            typeOk && (query.isBlank() || entry.id.contains(query, ignoreCase = true))
        }
        .take(120)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Versions", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Label("${state.installed.size} installed · ${state.remoteVersions.size} available")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Version dhundo, e.g. 1.21.6") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.width(300.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("release" to "Releases", "snapshot" to "Snapshots", "old" to "Old", "all" to "Sab").forEach { (key, label) ->
                val selected = typeFilter == key
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) TclBlue else Color.White.copy(alpha = 0.07f))
                        .clickable { typeFilter = key }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (state.busy && state.progress != null) {
            val progress = state.progress!!
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(progress.stage, fontWeight = FontWeight.SemiBold)
                        Label(if (progress.filesTotal > 0) "${progress.filesDone}/${progress.filesTotal}" else "")
                    }
                    LinearProgressIndicator(
                        progress = { progress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.installed.isNotEmpty() && query.isBlank()) {
                item {
                    Text(
                        "INSTALLED",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                items(state.installed) { id ->
                    VersionRow(
                        id = id,
                        type = "installed",
                        installed = true,
                        selected = state.selectedVersion == id,
                        busy = state.busy,
                        onInstall = { state.selectVersion(id) },
                        onPlay = { state.selectVersion(id); state.launchGame() }
                    )
                }
                item { Spacer(Modifier.height(10.dp)) }
                item {
                    Text(
                        "AVAILABLE",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            if (state.remoteVersions.isEmpty()) {
                item { Label(if (state.busy) "Manifest load ho raha hai…" else "Koi version nahi mila — internet check karo") }
            }

            items(visible) { entry ->
                VersionRow(
                    id = entry.id,
                    type = entry.type,
                    installed = entry.id in state.installed,
                    selected = state.selectedVersion == entry.id,
                    busy = state.busy,
                    onInstall = {
                        if (entry.id in state.installed) state.selectVersion(entry.id) else state.installVersion(entry.id)
                    },
                    onPlay = { state.selectVersion(entry.id); state.launchGame() }
                )
            }
        }
    }
}

@Composable
private fun VersionRow(
    id: String,
    type: String,
    installed: Boolean,
    selected: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onPlay: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        alpha = if (selected) 0.75f else 0.45f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(id, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Chip(
                        text = when (type) {
                            "snapshot" -> "snapshot"
                            "installed" -> "installed"
                            "old_beta", "old_alpha" -> "old"
                            else -> "release"
                        },
                        highlight = selected
                    )
                    if (selected) Chip("selected", highlight = true)
                }
                Label(if (installed) "Install hai — Play ke liye taiyaar" else "Download karna hoga (~250 MB)")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installed) {
                    Button(
                        onClick = onPlay,
                        enabled = !busy,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = TclBlue)
                    ) {
                        Text("Play", color = Color.White, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = onInstall,
                    enabled = !busy,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (installed) Color.White.copy(alpha = 0.12f) else TclBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (installed) "Select" else "Install",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
