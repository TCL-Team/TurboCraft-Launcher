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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.core.AppPaths
import com.tcl.desktop.core.AppState

@Composable
fun LogsScreen(state: AppState) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) listState.scrollToItem(state.logs.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Logs", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Label("Game log bhi: ${AppPaths.logs.absolutePath}")
            }
            Button(
                onClick = { state.logs.clear() },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f))
            ) {
                Icon(Icons.Outlined.DeleteSweep, null, tint = Color.White)
                Text("Clear", color = Color.White, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Box(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            GlassCard(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (state.logs.isEmpty()) {
                        item { Label("Khaali hai.") }
                    }
                    itemsIndexed(state.logs) { _, line ->
                        Text(
                            text = line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
