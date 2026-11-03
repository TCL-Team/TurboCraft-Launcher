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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                SectionHeader("Logs")
                Caption("Game logs: ${AppPaths.logs.absolutePath}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = "Open folder", onClick = { state.openLogsFolder() })
                SecondaryButton(text = "Clear", onClick = { state.logs.clear() })
            }
        }

        SectionCard(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (state.logs.isEmpty()) {
                    item { BodyText("Nothing here yet. Install or launch a version.", dim = true) }
                }
                itemsIndexed(state.logs) { _, line ->
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                        maxLines = 2
                    )
                }
            }
        }
    }
}
