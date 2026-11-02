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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
    }

    LauncherBackground(dark = dark) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(state = state)
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (state.screen) {
                        Screen.HOME -> HomeScreen(state)
                        Screen.VERSIONS -> VersionsScreen(state)
                        Screen.DOWNLOAD -> VersionsScreen(state, focusDownload = true)
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

@Composable
private fun Sidebar(state: AppState) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(TclBlue, TclBlueLight))),
            contentAlignment = Alignment.Center
        ) {
            Text(Brand.INITIALS, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(10.dp))

        NavItem(state.screen == Screen.HOME, "Home", { NavIcon(Icons.Outlined.Home, state.screen == Screen.HOME) }) {
            state.screen = Screen.HOME
        }
        NavItem(state.screen == Screen.VERSIONS, "Versions", { NavIcon(Icons.Outlined.Download, state.screen == Screen.VERSIONS) }) {
            state.screen = Screen.VERSIONS
            state.loadRemoteVersions()
        }
        NavItem(state.screen == Screen.MODS, "Mods", { NavIcon(Icons.Outlined.Extension, state.screen == Screen.MODS) }) {
            state.screen = Screen.MODS
        }
        NavItem(state.screen == Screen.ACCOUNTS, "Account", { NavIcon(Icons.Outlined.AccountCircle, state.screen == Screen.ACCOUNTS) }) {
            state.screen = Screen.ACCOUNTS
        }
        NavItem(state.screen == Screen.LOGS, "Logs", { NavIcon(Icons.Outlined.Article, state.screen == Screen.LOGS) }) {
            state.screen = Screen.LOGS
        }

        Spacer(Modifier.weight(1f))

        NavItem(state.screen == Screen.SETTINGS, "Settings", { NavIcon(Icons.Outlined.Settings, state.screen == Screen.SETTINGS) }) {
            state.screen = Screen.SETTINGS
        }
    }
}

@Composable
private fun NavIcon(icon: ImageVector, selected: Boolean) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) TclBlueLight else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun StatusBar(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.status,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.gameRunning) Chip("Game running", highlight = true)
            Chip("v${Brand.VERSION_NAME} · Windows")
        }
    }
}
