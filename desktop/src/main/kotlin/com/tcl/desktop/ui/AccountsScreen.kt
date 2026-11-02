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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.tcl.desktop.core.Account
import com.tcl.desktop.core.AccountType
import com.tcl.desktop.core.AppState

@Composable
fun AccountsScreen(state: AppState) {
    var name by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Accounts", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Label("Microsoft account game ke liye chahiye; offline single-player ke liye.")
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardTitle("Microsoft account")
                    Label(
                        if (state.deviceCode != null) {
                            "Browser me code daalo: ${state.deviceCode}"
                        } else {
                            "Login par click karo — browser khulega, wahan code daal dena. Launcher apne aap wait karega."
                        }
                    )
                    Button(
                        onClick = { state.startMicrosoftLogin() },
                        enabled = !state.busy,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = TclBlue)
                    ) {
                        Icon(Icons.Outlined.AccountCircle, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.busy) "Wait…" else "Microsoft se login", color = Color.White)
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardTitle("Offline account")
                    Label("Sasta/local testing ke liye. Server par premium account hi chalega.")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 16) name = it },
                            placeholder = { Text("Player name") },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.width(260.dp)
                        )
                        Button(
                            onClick = {
                                state.addOfflineAccount(name)
                                name = ""
                            },
                            enabled = name.isNotBlank(),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = TclBlue)
                        ) {
                            Icon(Icons.Outlined.PersonAdd, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Add", color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "SAVED ACCOUNTS",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }

        if (state.accounts.isEmpty()) {
            item { Label("Abhi koi account nahi hai.") }
        } else {
            items(state.accounts) { account ->
                AccountRow(
                    account = account,
                    selected = state.selectedAccount?.uuid == account.uuid,
                    onSelect = { state.selectAccount(account) },
                    onRemove = { state.removeAccount(account) }
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        alpha = if (selected) 0.75f else 0.45f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(account, size = 40)
                Column {
                    Text(account.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Label(
                        when (account.type) {
                            AccountType.MICROSOFT -> "Microsoft" + if (account.isExpired) " · refresh hoga" else " · valid"
                            AccountType.OFFLINE -> "Offline · ${account.uuid.take(8)}…"
                        }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected) Chip("active", highlight = true)
                OutlinedButton(
                    onClick = onRemove,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
