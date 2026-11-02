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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.Brand
import com.tcl.desktop.core.Account
import com.tcl.desktop.core.AccountType
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.Screen

@Composable
fun HomeScreen(state: AppState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { LaunchCard(state) }
        if (state.progress != null) item { ProgressCard(state) }
        item { InfoRow(state) }
        item { VersionsCard(state) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** Android launcher ka main card: welcome, version selector, Play, avatar */
@Composable
private fun LaunchCard(state: AppState) {
    var menuOpen by remember { mutableStateOf(false) }
    val account = state.selectedAccount
    val playable = state.selectedVersion.isNotBlank() || state.installed.isNotEmpty()

    Column {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1D6FD0),
                        Color(0xFF3B4BB0),
                        Color(0xFF2A2C3F)
                    )
                )
            )
            .padding(26.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Welcome back",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
                Text(
                    text = account?.name ?: "Add an account to play",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Version selector
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable { menuOpen = true }
                                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = state.selectedVersion.ifBlank { "Select a version" },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Outlined.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.85f))
                        }

                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (state.installed.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No versions installed yet") },
                                    onClick = {
                                        menuOpen = false
                                        state.screen = Screen.VERSIONS
                                    }
                                )
                            }
                            state.installed.forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id) },
                                    onClick = {
                                        state.selectVersion(id)
                                        menuOpen = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Install a new version…") },
                                onClick = {
                                    menuOpen = false
                                    state.screen = Screen.VERSIONS
                                }
                            )
                        }
                    }

                    PrimaryButton(
                        text = when {
                            state.gameRunning -> "Running"
                            state.busy -> "Please wait"
                            else -> "Play"
                        },
                        onClick = { state.play() },
                        enabled = !state.busy && !state.gameRunning && playable
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                    }

                    SecondaryButton(
                        text = "Versions",
                        onClick = { state.screen = Screen.VERSIONS }
                    ) {
                        Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }

            Avatar(account, size = 118)
        }
    }
    }
}

@Composable
fun Avatar(account: Account?, size: Int) {
    val initial = account?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = when (account?.type) {
        AccountType.MICROSOFT -> listOf(Color(0xFF0FA3E0), Color(0xFF4CC38A))
        AccountType.OFFLINE -> listOf(Color(0xFF3B82F6), Color(0xFF7C5CFF))
        null -> listOf(Color(0xFF3A4050), Color(0xFF22262F))
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.42f).sp
        )
    }
}

@Composable
private fun ProgressCard(state: AppState) {
    val progress = state.progress ?: return
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(progress.stage, fontWeight = FontWeight.SemiBold)
                Caption(
                    if (progress.filesTotal > 0) "${progress.filesDone} / ${progress.filesTotal} files" else ""
                )
            }
            LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
            )
            Caption(progress.currentFile.ifBlank { "Starting…" })
        }
    }
}

@Composable
private fun InfoRow(state: AppState) {
    val javaCount = remember { com.tcl.desktop.core.JavaManager.detectAll() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        InfoCard(
            title = "Installed",
            value = if (state.installed.isEmpty()) "None" else "${state.installed.size} version(s)",
            hint = state.installed.firstOrNull() ?: "Install one to start",
            modifier = Modifier.weight(1f)
        ) { state.screen = Screen.VERSIONS }

        InfoCard(
            title = "Memory",
            value = "${state.settings.memoryMb} MB",
            hint = "Change in Settings",
            modifier = Modifier.weight(1f)
        ) { state.screen = Screen.SETTINGS }

        InfoCard(
            title = "Java",
            value = if (javaCount.isEmpty()) "Auto download" else "${javaCount.size} found",
            hint = javaCount.firstOrNull()?.let { "Java ${it.majorVersion} · ${it.source}" } ?: "Temurin on launch",
            modifier = Modifier.weight(1f)
        ) { state.screen = Screen.SETTINGS }

        InfoCard(
            title = "Game folder",
            value = "Open",
            hint = ".minecraft layout",
            modifier = Modifier.weight(1f)
        ) { state.openGameFolder() }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SectionCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Caption(title.uppercase())
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Caption(hint.take(34))
        }
    }
}

@Composable
private fun VersionsCard(state: AppState) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Installed versions", trailing = {
            Text(
                text = "Manage",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { state.screen = Screen.VERSIONS }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        })
        if (state.installed.isEmpty()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BodyText("Nothing installed yet")
                Caption("Open Versions, pick a Minecraft release and press Install — or just hit Play and it will download first.")
            }
        } else {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                state.installed.forEach { id ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.play(id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(id, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (id == state.selectedVersion) Tag("active", highlight = true)
                            if (id.startsWith("FabricInstaller") || id.startsWith("fabric-loader")) {
                                Tag("fabric")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Play", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
