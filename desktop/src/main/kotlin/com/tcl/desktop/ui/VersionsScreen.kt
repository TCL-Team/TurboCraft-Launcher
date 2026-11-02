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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.LoaderType

@Composable
fun VersionsScreen(state: AppState) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("release") }

    if (state.loaderTargetVersion != null) {
        LoaderPickerDialog(state)
    }

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
        .take(150)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SectionHeader("Versions")
                    Caption("${state.installed.size} installed · ${state.remoteVersions.size} available")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search versions, e.g. 1.21") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.width(280.dp)
                    )
                    SecondaryButton(
                        text = "Refresh",
                        enabled = !state.busy,
                        onClick = { state.loadRemoteVersions(force = true) }
                    ) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "release" to "Releases",
                    "snapshot" to "Snapshots",
                    "old" to "Old versions",
                    "all" to "All"
                ).forEach { (key, label) ->
                    Pill(label, selected = typeFilter == key, onClick = { typeFilter = key })
                }
            }
        }

        if (state.progress != null && state.busy) {
            item {
                val progress = state.progress!!
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(progress.stage, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Caption(if (progress.filesTotal > 0) "${progress.filesDone}/${progress.filesTotal}" else "")
                        }
                        LinearProgressIndicator(
                            progress = { progress.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                        )
                        Caption(progress.currentFile.ifBlank { "…" })
                    }
                }
            }
        }

        if (state.installed.isNotEmpty() && query.isBlank() && typeFilter != "old") {
            item { Caption("INSTALLED") }
            items(state.installed) { id ->
                VersionRow(
                    id = id,
                    type = if (id.startsWith("fabric-loader")) "fabric" else "installed",
                    installed = true,
                    selected = state.selectedVersion == id,
                    busy = state.busy,
                    installLabel = null,
                    onSelect = { state.selectVersion(id) },
                    onPlay = { state.play(id) },
                    onFabric = if (id.startsWith("fabric-loader")) null else ({ state.installFabric(id) })
                )
            }
            item { Caption("AVAILABLE") }
        }

        if (state.remoteVersions.isEmpty()) {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BodyText(if (state.remoteLoading) "Loading version list…" else "Couldn't load the version list")
                        Caption("Check your internet connection, then press Refresh.")
                    }
                }
            }
        }

        items(visible) { entry ->
            val installed = entry.id in state.installed
            VersionRow(
                id = entry.id,
                type = entry.type,
                installed = installed,
                selected = state.selectedVersion == entry.id,
                busy = state.busy,
                installLabel = if (installed) null else "Install",
                onSelect = {
                    if (installed) state.selectVersion(entry.id) else state.openLoaderPicker(entry.id)
                },
                onPlay = { state.play(entry.id) },
                onFabric = if (installed) ({ state.installFabric(entry.id) }) else null
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun VersionRow(
    id: String,
    type: String,
    installed: Boolean,
    selected: Boolean,
    busy: Boolean,
    installLabel: String?,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onFabric: (() -> Unit)? = null,
) {
    SectionCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(id, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Tag(
                    text = when (type) {
                        "snapshot" -> "snapshot"
                        "installed" -> "installed"
                        "fabric" -> "fabric"
                        "old_beta", "old_alpha" -> "old"
                        else -> "release"
                    },
                    highlight = selected
                )
                if (selected) Tag("selected", highlight = true)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onFabric != null) {
                    SecondaryButton(text = "+ Fabric", enabled = !busy, onClick = onFabric)
                }
                if (installed) {
                    PrimaryButton(text = "Play", enabled = !busy, onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                } else if (installLabel != null) {
                    SecondaryButton(text = installLabel, enabled = !busy, onClick = onSelect)
                }
            }
        }
    }
}

/**
 * Android edition jaisa: version chunne ke baad loader choose karo
 * (Vanilla / Fabric / Quilt / NeoForge / Forge).
 */
@Composable
private fun LoaderPickerDialog(state: AppState) {
    val target = state.loaderTargetVersion ?: return
    val available = listOf(
        LoaderType.VANILLA,
        LoaderType.FABRIC,
        LoaderType.QUILT,
        LoaderType.NEOFORGE,
        LoaderType.FORGE
    )

    Dialog(onDismissRequest = { state.closeLoaderPicker() }) {
        SectionCard(modifier = Modifier.width(560.dp)) {
            CardTitle("Install $target")
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BodyText("Choose a mod loader")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { type ->
                        Pill(
                            text = type.label,
                            selected = state.loaderType == type,
                            onClick = { state.selectLoader(type) }
                        )
                    }
                }

                if (state.loaderType != LoaderType.VANILLA) {
                    if (state.loaderVersionsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            BodyText("Loading ${state.loaderType.label} versions…")
                        }
                    } else if (state.loaderVersionList.isEmpty()) {
                        BodyText("No ${state.loaderType.label} build found for $target.", dim = true)
                    } else {
                        Caption("${state.loaderVersionList.size} builds · newest first")
                        LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.loaderVersionList.take(40)) { loaderVersion ->
                                val selected = state.selectedLoaderVersion == loaderVersion.version
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                        )
                                        .clickable { state.selectLoader(state.loaderType, loaderVersion.version) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(loaderVersion.version, fontSize = 13.sp)
                                    if (loaderVersion.stable) Tag("stable")
                                }
                            }
                        }
                    }
                } else {
                    BodyText("Plain Minecraft, no mod loader. Fabric/Quilt/Forge can be added later.")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { state.closeLoaderPicker() }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(
                        text = if (state.loaderType == LoaderType.VANILLA) "Install" else "Install ${state.loaderType.label}",
                        enabled = !state.busy && (
                            state.loaderType == LoaderType.VANILLA ||
                                (state.loaderVersionList.isNotEmpty() && state.selectedLoaderVersion != null)
                            ),
                        onClick = { state.installWithLoader() }
                    )
                }
            }
        }
    }
}
