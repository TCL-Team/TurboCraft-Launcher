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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.core.AppState
import com.tcl.desktop.core.ModFile
import com.tcl.desktop.core.Mods

@Composable
fun ModsScreen(state: AppState) {
    // refresh tick — file system Compose observe nahi karta
    var tick by remember { mutableIntStateOf(0) }
    val mods = remember(tick) { Mods.list() }
    val enabledCount = mods.count { it.enabled }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Mods", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Label("${mods.size} mods · $enabledCount chalu · ${Mods.folder.absolutePath}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { Mods.openFolder() },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = TclBlue)
                    ) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Folder kholo", color = Color.White, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { tick++ },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(Icons.Outlined.Refresh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (mods.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardTitle("Abhi koi mod nahi hai")
                        Label("Folder khol kar `.jar` files daal do — Fabric/Quilt mods chalenge. Phir refresh dabao.")
                        Label("Mods har version ke saath shared hote hain (game/mods).")
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
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ModRow(mod: ModFile, onToggle: () -> Unit, onDelete: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        alpha = if (mod.enabled) 0.55f else 0.32f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(mod.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Label("${mod.sizeLabel} · " + if (mod.enabled) "chalu" else "band")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = mod.enabled, onCheckedChange = { onToggle() })
                OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(50), modifier = Modifier.height(34.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
