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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.Brand
import com.tcl.desktop.core.AppPaths
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.JavaManager
import com.tcl.desktop.core.JavaRuntime

@Composable
fun SettingsScreen(state: AppState) {
    val settings = state.settings
    val javas = rememberJavas()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                SectionHeader("Settings")
                Caption("Everything is stored in ${AppPaths.root.absolutePath}")
            }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Memory")
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BodyText("Allocated RAM")
                        Text("${settings.memoryMb} MB", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Slider(
                        value = settings.memoryMb.toFloat(),
                        onValueChange = { value ->
                            val rounded = (value / 512).toInt() * 512
                            state.updateSettings { it.memoryMb = rounded.coerceIn(1024, 16384) }
                        },
                        valueRange = 1024f..16384f
                    )
                    Caption("2–4 GB is fine for vanilla and light modpacks. Use 6 GB or more with shaders.")
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Java")
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BodyText(
                        if (javas.isEmpty()) {
                            "No Java runtime found on this PC. The launcher downloads Temurin automatically on first launch."
                        } else {
                            "Detected: " + javas.joinToString(", ") { "Java ${it.majorVersion} (${it.source})" }
                        }
                    )
                    OutlinedTextField(
                        value = settings.javaPath,
                        onValueChange = { value -> state.updateSettings { it.javaPath = value } },
                        label = { Text("Custom Java path (empty = automatic)") },
                        placeholder = { Text("C:\\Program Files\\Java\\jdk-21\\bin\\java.exe") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Caption("Java 8 is used for 1.16 and older, 17 for 1.18–1.19, 21 for 1.20+.")
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Game window")
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var widthText by remember { mutableStateOf(settings.windowWidth.toString()) }
                    var heightText by remember { mutableStateOf(settings.windowHeight.toString()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { value ->
                                widthText = value.filter { it.isDigit() }.take(4)
                                widthText.toIntOrNull()?.takeIf { it >= 640 }?.let { w ->
                                    state.updateSettings { it.windowWidth = w }
                                }
                            },
                            label = { Text("Width") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.width(150.dp)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { value ->
                                heightText = value.filter { it.isDigit() }.take(4)
                                heightText.toIntOrNull()?.takeIf { it >= 480 }?.let { h ->
                                    state.updateSettings { it.windowHeight = h }
                                }
                            },
                            label = { Text("Height") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.width(150.dp)
                        )
                    }
                    OutlinedTextField(
                        value = settings.jvmArgs,
                        onValueChange = { value -> state.updateSettings { it.jvmArgs = value } },
                        label = { Text("Extra JVM arguments") },
                        placeholder = { Text("-XX:+UseG1GC") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Launcher")
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow(
                        title = "Dark theme",
                        subtitle = "Matches the Android launcher",
                        checked = settings.theme != "light"
                    ) { dark ->
                        state.updateSettings { it.theme = if (dark) "dark" else "light" }
                    }
                    ToggleRow(
                        title = "Discord Rich Presence",
                        subtitle = "Show what you are playing (when available)",
                        checked = settings.discordPresence
                    ) { enabled ->
                        state.updateSettings { it.discordPresence = enabled }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(text = "Open game folder", onClick = { state.openGameFolder() }) {
                            Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        SecondaryButton(text = "Open logs", onClick = { state.openLogsFolder() })
                        SecondaryButton(text = "View logs", onClick = { state.screen = com.tcl.desktop.core.Screen.LOGS })
                    }
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("About")
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyText("${Brand.APP_NAME} · v${Brand.VERSION_NAME} (build ${Brand.VERSION_CODE})")
                    Caption("${Brand.VENDOR} · ${Brand.HOME_URL}")
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        "Change the launcher name or version in desktop/gradle.properties — " +
                            "window title, UI, data folder and installer name all come from there."
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Caption(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun rememberJavas(): List<JavaRuntime> =
    androidx.compose.runtime.remember { JavaManager.detectAll() }
