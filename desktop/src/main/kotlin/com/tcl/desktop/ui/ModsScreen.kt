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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
                    SectionHeader("Mods")
                    Caption("${mods.size} mods · $enabledCount enabled · ${Mods.folder.absolutePath}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = "Open folder", onClick = { Mods.openFolder() }) {
                        Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    SecondaryButton(text = "Refresh", onClick = { tick++ })
                }
            }
        }

        if (mods.isEmpty()) {
            item {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("No mods yet")
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyText("Open the folder and drop your .jar mods in. They load with Fabric versions.")
                        Caption("Mods are shared between all versions (game/mods).")
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
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
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
