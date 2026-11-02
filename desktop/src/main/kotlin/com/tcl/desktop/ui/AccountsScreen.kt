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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.tcl.desktop.core.Account
import com.tcl.desktop.core.AccountType
import com.tcl.desktop.core.AppState

@Composable
fun AccountsScreen(state: AppState) {
    var name by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                SectionHeader("Accounts")
                Caption("Sign in with Microsoft to play online. Offline accounts are for single-player and testing.")
            }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Microsoft account")
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BodyText(
                        if (state.deviceCode != null) {
                            "Your browser is open. Enter this code: ${state.deviceCode}"
                        } else {
                            "Your browser opens, you sign in, and the launcher picks up the token automatically — " +
                                "no password ever touches the launcher."
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            text = if (state.busy) "Waiting…" else "Sign in with Microsoft",
                            enabled = !state.busy,
                            onClick = { state.startMicrosoftLogin(useDeviceCode = false) }
                        ) {
                            Icon(Icons.Outlined.AccountCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        SecondaryButton(
                            text = "Use a device code instead",
                            enabled = !state.busy,
                            onClick = { state.startMicrosoftLogin(useDeviceCode = true) }
                        )
                        if (state.deviceCode != null) {
                            SecondaryButton(text = "Copy code", onClick = {
                                runCatching {
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                        .setContents(java.awt.datatransfer.StringSelection(state.deviceCode), null)
                                }
                            })
                        }
                    }
                    Caption(
                        "Using app id ${state.msClientId}. If Microsoft rejects it, register a free app " +
                            "in Settings → Microsoft app id (takes 2 minutes)."
                    )
                }
            }
        }

        item {
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Offline account")
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BodyText("A username only. Only works on servers that allow cracked accounts.")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 16) name = it },
                            placeholder = { Text("Username") },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.width(260.dp)
                        )
                        PrimaryButton(
                            text = "Add account",
                            enabled = name.isNotBlank(),
                            onClick = {
                                state.addOfflineAccount(name)
                                name = ""
                            }
                        ) {
                            Icon(Icons.Outlined.PersonAdd, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }
            }
        }

        item { Caption("SAVED ACCOUNTS") }

        if (state.accounts.isEmpty()) {
            item { BodyText("No accounts yet.", dim = true) }
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
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp)
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
                    Caption(
                        when (account.type) {
                            AccountType.MICROSOFT ->
                                if (account.isExpired) "Microsoft · will refresh on launch" else "Microsoft · signed in"
                            AccountType.OFFLINE -> "Offline · ${account.uuid.take(8)}…"
                        }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected) Tag("active", highlight = true)
                SecondaryButton(text = "Remove", onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Delete,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
    }
}
