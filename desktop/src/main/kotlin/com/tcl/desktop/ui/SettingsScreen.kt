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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.Brand
import com.tcl.desktop.core.AppPaths
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.JavaManager

@Composable
fun SettingsScreen(state: AppState) {
    val settings = state.settings
    val javas = rememberJavas()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Label("Sab kuch %APPDATA%\\${Brand.IDENTIFIER} me rehta hai.")
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardTitle("Memory")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Label("RAM allocation")
                        Text("${settings.memoryMb} MB", fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = settings.memoryMb.toFloat(),
                        onValueChange = { value ->
                            val rounded = (value / 512).toInt() * 512
                            state.updateSettings { it.memoryMb = rounded.coerceIn(1024, 16384) }
                        },
                        valueRange = 1024f..16384f
                    )
                    Label("2 GB - 4 GB chhote modpacks ke liye kaafi; shaders ke saath 6 GB+.")
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardTitle("Java")
                    Label(
                        if (javas.isEmpty()) {
                            "Koi Java nahi mila — launch par Temurin apne aap download hoga."
                        } else {
                            "Mile hue runtimes: " + javas.joinToString(", ") { "Java ${it.majorVersion} (${it.source})" }
                        }
                    )
                    OutlinedTextField(
                        value = settings.javaPath,
                        onValueChange = { value -> state.updateSettings { it.javaPath = value } },
                        label = { Text("Custom Java path (khaali = auto)") },
                        placeholder = { Text("C:\\Program Files\\Java\\jdk-21\\bin\\java.exe") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardTitle("Game window")
                    var widthText by remember { mutableStateOf(settings.windowWidth.toString()) }
                    var heightText by remember { mutableStateOf(settings.windowHeight.toString()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { value ->
                                widthText = value.filter { it.isDigit() }.take(4)
                                widthText.toIntOrNull()?.let { w ->
                                    if (w >= 640) state.updateSettings { it.windowWidth = w }
                                }
                            },
                            label = { Text("Width") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.width(160.dp)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { value ->
                                heightText = value.filter { it.isDigit() }.take(4)
                                heightText.toIntOrNull()?.let { h ->
                                    if (h >= 480) state.updateSettings { it.windowHeight = h }
                                }
                            },
                            label = { Text("Height") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.width(160.dp)
                        )
                    }
                    OutlinedTextField(
                        value = settings.jvmArgs,
                        onValueChange = { value -> state.updateSettings { it.jvmArgs = value } },
                        label = { Text("Extra JVM arguments") },
                        placeholder = { Text("-XX:+UseG1GC -Dfile.encoding=UTF-8") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardTitle("Launcher")
                    ToggleRow(
                        title = "Dark theme",
                        subtitle = "TCL ka default look",
                        checked = settings.theme != "light"
                    ) { dark ->
                        state.updateSettings { it.theme = if (dark) "dark" else "light" }
                    }
                    ToggleRow(
                        title = "Discord Rich Presence",
                        subtitle = "Doston ko dikhe ki tum kya khel rahe ho",
                        checked = settings.discordPresence
                    ) { enabled ->
                        state.updateSettings { it.discordPresence = enabled }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { state.openGameFolder() },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f))
                        ) {
                            Icon(Icons.Outlined.FolderOpen, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Game folder", color = Color.White)
                        }
                        Button(
                            onClick = { state.screen = com.tcl.desktop.core.Screen.LOGS },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f))
                        ) {
                            Text("Logs dekho", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Install folder: ${AppPaths.root.absolutePath}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTitle("About")
                    Label("${Brand.APP_NAME} · v${Brand.VERSION_NAME} (build ${Brand.VERSION_CODE})")
                    Label("${Brand.VENDOR} · ${Brand.HOME_URL}")
                    Spacer(Modifier.height(4.dp))
                    Label(
                        "Naam / version badalna hai? Sirf desktop/gradle.properties edit karo — " +
                            "window title, UI, data folder, exe/MSI ka naam, sab wahan se aata hai."
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
            Label(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun rememberJavas(): List<com.tcl.desktop.core.JavaRuntime> =
    androidx.compose.runtime.remember { JavaManager.detectAll() }
