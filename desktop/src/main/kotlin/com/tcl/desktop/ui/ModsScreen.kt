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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.ModFile
import com.tcl.desktop.core.Mods
import com.tcl.desktop.core.Packs
import com.tcl.desktop.core.ProjectHit

@Composable
fun ModsScreen(state: AppState) {
    var tab by remember { mutableStateOf("installed") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                SectionHeader("Mods & Modpacks")
                Caption(
                    "Target: ${Packs.displayName(state.selectedVersion.ifBlank { "no version" })} · " +
                        Mods.folderLabel(state.selectedVersion)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(text = "Open folder", onClick = { Mods.openFolder(state.selectedVersion) }) {
                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Installed", selected = tab == "installed", onClick = { tab = "installed" })
            Pill("Browse mods", selected = tab == "mods", onClick = { tab = "mods" })
            Pill("Modpacks", selected = tab == "modpacks", onClick = { tab = "modpacks" })
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            "mods" -> BrowseTab(state, "mod")
            "modpacks" -> BrowseTab(state, "modpack")
            else -> InstalledTab(state)
        }
    }
}

@Composable
private fun InstalledTab(state: AppState) {
    var tick by remember { mutableIntStateOf(0) }
    val mods = remember(tick, state.selectedVersion) { Mods.list(state.selectedVersion) }
    val enabled = mods.count { it.enabled }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Caption("${mods.size} mods · $enabled enabled")
                SecondaryButton(text = "Refresh", onClick = { tick++ })
            }
        }

        if (mods.isEmpty()) {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("No mods yet")
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyText("Use “Browse mods” to install from Modrinth, or drop .jar files into the folder.")
                        Caption("Each version has its own mods folder, so modpacks stay separate.")
                    }
                }
            }
        } else {
            items(mods) { mod ->
                ModRow(
                    mod = mod,
                    onToggle = {
                        Mods.toggle(mod)
                        tick++
                    },
                    onDelete = {
                        Mods.delete(mod)
                        tick++
                    }
                )
            }
        }
    }
}

@Composable
private fun ModRow(mod: ModFile, onToggle: () -> Unit, onDelete: () -> Unit) {
    SectionCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(mod.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Caption("${mod.sizeLabel} · " + if (mod.enabled) "enabled" else "disabled")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = mod.enabled, onCheckedChange = { onToggle() })
                SecondaryButton(text = "Remove", onClick = onDelete)
            }
        }
    }
}

@Composable
private fun BrowseTab(state: AppState, type: String) {
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(if (type == "mod") "Search mods on Modrinth" else "Search modpacks") },
                leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = "Search",
                enabled = !state.browseLoading,
                onClick = { state.searchProjects(query, type) }
            )
            SecondaryButton(
                text = "Popular",
                enabled = !state.browseLoading,
                onClick = { state.searchProjects("", type) }
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            state.browseLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                BodyText("Loading from Modrinth…")
            }
            state.browseError != null -> SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) { ErrorText(state.browseError!!) }
            }
            state.browseResults.isEmpty() -> SectionCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText(if (type == "mod") "Search mods that match your selected version and loader." else "Search modpacks — one click installs the whole pack.")
                    Caption("Powered by Modrinth")
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.browseResults) { hit ->
                    ProjectRow(
                        hit = hit,
                        busy = state.busy,
                        isModpack = type == "modpack",
                        onInstall = {
                            if (type == "modpack") state.installModpack(hit) else state.installMod(hit)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectRow(hit: ProjectHit, busy: Boolean, isModpack: Boolean, onInstall: () -> Unit) {
    SectionCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(hit.title.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(hit.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Tag(if (isModpack) "modpack" else "mod")
                    }
                    Caption("${formatDownloads(hit.downloads)} downloads · by ${hit.author}")
                    BodyText(hit.description.take(120), dim = true, maxLines = 2)
                }
            }
            PrimaryButton(
                text = if (isModpack) "Install pack" else "Install",
                enabled = !busy,
                onClick = onInstall
            )
        }
    }
}

private fun formatDownloads(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.0fK".format(count / 1_000.0)
    else -> count.toString()
}
