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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.tcl.desktop.core.Account
import com.tcl.desktop.core.AccountType
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.Screen

@Composable
fun HomeScreen(state: AppState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { HeroCard(state) }
        item { QuickRow(state) }
        if (state.progress != null) item { ProgressCard(state) }
        item { RecentLogsCard(state) }
    }
}

@Composable
private fun HeroCard(state: AppState) {
    var menuOpen by remember { mutableStateOf(false) }
    val account = state.selectedAccount

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        TclBlue.copy(alpha = 0.85f),
                        Color(0xFF6D3BE0).copy(alpha = 0.75f),
                        Color(0xFF2B3040).copy(alpha = 0.92f)
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "TurboCraft Launcher",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Windows edition · Minecraft Java",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(4.dp))

                // Version selector
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { menuOpen = true }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            state.selectedVersion.ifBlank { "Koi version select karo" },
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.8f))
                    }

                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val versions = state.installed.ifEmpty { state.remoteVersions.map { it.id }.take(20) }
                        if (versions.isEmpty()) {
                            DropdownMenuItem(text = { Text("Kuch install nahi hai") }, onClick = { menuOpen = false })
                        }
                        versions.forEach { id ->
                            DropdownMenuItem(
                                text = { Text(id) },
                                onClick = {
                                    if (id in state.installed) {
                                        state.selectVersion(id)
                                    } else {
                                        state.screen = Screen.VERSIONS
                                    }
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { state.launchGame() },
                        enabled = !state.busy && !state.gameRunning,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        modifier = Modifier.height(50.dp)
                    ) {
                        if (state.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = TclBlue
                            )
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Icon(Icons.Filled.PlayArrow, null, tint = TclBlue)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = when {
                                state.gameRunning -> "Running…"
                                state.busy -> "Please wait"
                                else -> "Play"
                            },
                            color = TclBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { state.screen = Screen.ACCOUNTS }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Avatar(account, size = 26)
                        Text(
                            account?.name ?: "Account add karo",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Avatar(account, size = 132)
        }
    }
}

@Composable
fun Avatar(account: Account?, size: Int) {
    val initial = account?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val gradient = when (account?.type) {
        AccountType.MICROSOFT -> listOf(Color(0xFF00A4EF), Color(0xFF7FBA00))
        AccountType.OFFLINE -> listOf(TclBlue, TclBlueLight)
        null -> listOf(Color(0xFF4A5163), Color(0xFF2B3040))
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
private fun QuickRow(state: AppState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuickCard(
            title = "Installed",
            value = "${state.installed.size} version",
            hint = state.installed.firstOrNull() ?: "abhi khaali",
            modifier = Modifier.weight(1f)
        ) { state.screen = Screen.VERSIONS }

        QuickCard(
            title = "Memory",
            value = "${state.settings.memoryMb} MB",
            hint = "Settings me badlo",
            modifier = Modifier.weight(1f)
        ) { state.screen = Screen.SETTINGS }

        QuickCard(
            title = "Game folder",
            value = "Kholo",
            hint = ".minecraft layout",
            modifier = Modifier.weight(1f)
        ) { state.openGameFolder() }

        QuickCard(
            title = "Account",
            value = state.selectedAccount?.name ?: "None",
            hint = state.selectedAccount?.type?.name?.lowercase() ?: "offline / microsoft",
            modifier = Modifier.weight(1f)
        ) { state.screen = Screen.ACCOUNTS }
    }
}

@Composable
private fun QuickCard(
    title: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
        }
    }
}

@Composable
private fun ProgressCard(state: AppState) {
    val progress = state.progress ?: return
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(progress.stage, fontWeight = FontWeight.SemiBold)
                Text(
                    if (progress.filesTotal > 0) "${progress.filesDone}/${progress.filesTotal}" else "",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
            )
            Text(
                progress.currentFile.takeIf { it.isNotBlank() } ?: "…",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecentLogsCard(state: AppState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardTitle("Launcher log")
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { state.screen = Screen.LOGS }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Saare logs", fontSize = 12.sp, color = TclBlueLight)
                }
            }
            val tail = state.logs.takeLast(5)
            if (tail.isEmpty()) {
                Label("Abhi kuch nahi hua. Play dabao ya version install karo.")
            } else {
                tail.forEach { line ->
                    Text(line, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}
