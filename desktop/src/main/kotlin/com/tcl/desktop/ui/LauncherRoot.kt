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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.Brand
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.Screen

@Composable
fun LauncherRoot(state: AppState, dark: Boolean, onExit: () -> Unit) {
    LaunchedEffect(Unit) {
        state.refreshInstalled()
        state.loadRemoteVersions()
        state.checkForUpdates(quiet = true)
    }

    LauncherBackground(dark = dark) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(state)
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(state)
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (state.screen) {
                        Screen.HOME -> HomeScreen(state)
                        Screen.VERSIONS, Screen.INSTALL -> VersionsScreen(state)
                        Screen.MODS -> ModsScreen(state)
                        Screen.ACCOUNTS -> AccountsScreen(state)
                        Screen.SETTINGS -> SettingsScreen(state)
                        Screen.LOGS -> LogsScreen(state)
                    }
                }
                StatusBar(state)
            }
        }
    }
}

/** Android edition ka navigation rail — icon + label, selected item highlight */
@Composable
private fun NavigationRail(state: AppState) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, AccentSoft))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = Brand.INITIALS,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(14.dp))

        RailItem(Icons.Outlined.Home, "Home", state.screen == Screen.HOME) { state.screen = Screen.HOME }
        RailItem(Icons.Outlined.Widgets, "Install", state.screen == Screen.VERSIONS || state.screen == Screen.INSTALL) {
            state.screen = Screen.VERSIONS
            state.loadRemoteVersions()
        }
        RailItem(Icons.Outlined.Extension, "Mods", state.screen == Screen.MODS) { state.screen = Screen.MODS }
        RailItem(Icons.Outlined.AccountCircle, "Account", state.screen == Screen.ACCOUNTS) { state.screen = Screen.ACCOUNTS }
        RailItem(Icons.Outlined.Article, "Logs", state.screen == Screen.LOGS) { state.screen = Screen.LOGS }

        Spacer(Modifier.weight(1f))

        RailItem(Icons.Outlined.Settings, "Settings", state.screen == Screen.SETTINGS) { state.screen = Screen.SETTINGS }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        Color.Transparent
    }
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        AnimatedVisibility(visible = selected) {
            Text(text = label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TopBar(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = Brand.APP_NAME,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Caption(
                (if (state.installed.isEmpty()) "No versions installed" else "${state.installed.size} versions installed") +
                    " · " + com.tcl.desktop.core.Packs.displayName(state.selectedVersion.ifBlank { "nothing selected" })
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.gameRunning) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Minecraft is running",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "v${Brand.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    val color = if (state.statusIsError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (state.statusIsError) MaterialTheme.colorScheme.error else AccentSoft)
            )
            Text(
                text = state.status,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.busy) {
                Text(
                    "Working…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Windows · ${Brand.VERSION_CODE}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
