/*
 * TurboCraft Launcher — desktop (Windows) edition
 * Copyright (C) 2025-2026 TCL Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.tcl.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, VERSIONS, DOWNLOAD, MODS, ACCOUNTS, SETTINGS, LOGS }

/**
 * Poora launcher state ek jagah — Compose UI isko observe karta hai.
 */
class AppState(private val scope: CoroutineScope) {
    private val installer = GameInstaller()

    var settings by mutableStateOf(SettingsStore.load())
        private set
    var accounts: List<Account> by mutableStateOf(Accounts.load())
        private set
    var selectedAccount by mutableStateOf(accounts.firstOrNull())
    var screen by mutableStateOf(Screen.HOME)
    var installed by mutableStateOf(installer.installedVersions())
        private set
    var remoteVersions by mutableStateOf<List<ManifestEntry>>(emptyList())
        private set
    var versionFilter by mutableStateOf("")
    var status by mutableStateOf("Taiyaar hai")
    var progress by mutableStateOf<InstallProgress?>(null)
    var busy by mutableStateOf(false)
    var gameRunning by mutableStateOf(false)
    var deviceCode by mutableStateOf<String?>(null)

    val logs = mutableStateListOf<String>()

    val selectedVersion: String get() = settings.selectedVersion

    private fun ui(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    fun log(line: String) {
        ui {
            logs += line
            if (logs.size > 400) logs.removeRange(0, logs.size - 400)
        }
    }

    fun updateSettings(block: (Settings) -> Unit) {
        val copy = settings.copy().also(block)
        settings = copy
        SettingsStore.save(copy)
    }

    fun selectVersion(id: String) {
        updateSettings { it.selectedVersion = id }
    }

    fun selectAccount(account: Account) {
        selectedAccount = account
        updateSettings { it.selectedAccount = account.uuid }
    }

    fun refreshInstalled() {
        installed = installer.installedVersions()
        if (settings.selectedVersion.isBlank() || settings.selectedVersion !in installed) {
            installed.firstOrNull()?.let { selectVersion(it) }
        }
    }

    fun loadRemoteVersions() {
        if (remoteVersions.isNotEmpty() || busy) return
        busy = true
        status = "Minecraft versions load ho rahe hain…"
        scope.launch(Dispatchers.IO) {
            runCatching { installer.listVersions() }
                .onSuccess { manifest ->
                    ui {
                        remoteVersions = manifest.versions
                        busy = false
                        status = "${manifest.versions.size} versions mile"
                    }
                }
                .onFailure { error ->
                    ui {
                        busy = false
                        status = "Versions load fail: ${error.message}"
                    }
                }
        }
    }

    fun installVersion(versionId: String) {
        if (busy) return
        busy = true
        progress = InstallProgress(stage = "Shuru…")
        scope.launch(Dispatchers.IO) {
            runCatching {
                installer.install(versionId) { p -> ui { progress = p } }
            }.onSuccess {
                ui {
                    busy = false
                    progress = null
                    refreshInstalled()
                    selectVersion(versionId)
                    screen = Screen.HOME
                    status = "$versionId install ho gaya"
                }
            }.onFailure { error ->
                ui {
                    busy = false
                    progress = null
                    status = "Install fail: ${error.message}"
                }
                log("Install error: ${error.message}")
            }
        }
    }

    /** Vanilla version ke upar Fabric loader install karta hai */
    fun installFabric(baseVersion: String) {
        if (busy) return
        busy = true
        progress = InstallProgress(stage = "Fabric loader dhundh raha hoon…")
        scope.launch(Dispatchers.IO) {
            runCatching {
                FabricInstaller.install(baseVersion, onProgress = { p -> ui { progress = p } })
            }.onSuccess { id ->
                ui {
                    busy = false
                    progress = null
                    refreshInstalled()
                    selectVersion(id)
                    status = "Fabric ready: $id"
                }
            }.onFailure { error ->
                ui {
                    busy = false
                    progress = null
                    status = "Fabric install fail: ${error.message}"
                }
                log("Fabric error: ${error.message}")
            }
        }
    }

    fun addOfflineAccount(name: String) {
        if (name.isBlank()) return
        val account = Account(
            type = AccountType.OFFLINE,
            name = name.trim(),
            uuid = Accounts.offlineUuid(name.trim()),
            accessToken = "0"
        )
        accounts = accounts.filterNot { it.name == account.name && it.type == AccountType.OFFLINE } + account
        Accounts.save(accounts)
        selectAccount(account)
        status = "Offline account jud gaya: ${account.name}"
    }

    fun removeAccount(account: Account) {
        accounts = accounts.filterNot { it.uuid == account.uuid }
        Accounts.save(accounts)
        if (selectedAccount?.uuid == account.uuid) {
            selectedAccount = accounts.firstOrNull()
        }
    }

    fun startMicrosoftLogin() {
        if (busy) return
        busy = true
        status = "Microsoft login shuru…"
        scope.launch(Dispatchers.IO) {
            runCatching {
                val (info, code) = MicrosoftAuth.startLogin()
                ui {
                    deviceCode = info.userCode
                    status = "Browser me ${info.verificationUri} kholo, code: ${info.userCode}"
                }
                // Browser khol do
                runCatching {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(info.verificationUri))
                }
                MicrosoftAuth.loginWithDeviceCode(info, code) { message -> ui { status = message } }
            }.onSuccess { account ->
                ui {
                    busy = false
                    deviceCode = null
                    accounts = (accounts.filterNot { it.uuid == account.uuid } + account)
                    Accounts.save(accounts)
                    selectAccount(account)
                    status = "Login ho gaya: ${account.name}"
                }
            }.onFailure { error ->
                ui {
                    busy = false
                    deviceCode = null
                    status = "Login fail: ${error.message}"
                }
                log("Login error: ${error.message}")
            }
        }
    }

    fun launchGame() {
        val account = selectedAccount ?: run {
            status = "Pehle account add karo"
            screen = Screen.ACCOUNTS
            return
        }
        if (gameRunning) {
            status = "Game already chal raha hai"
            return
        }
        val versionId = selectedVersion.ifBlank { installed.firstOrNull().orEmpty() }
        if (versionId.isBlank()) {
            status = "Pehle koi version install karo"
            screen = Screen.DOWNLOAD
            return
        }

        status = "Launch ho raha hai…"
        scope.launch(Dispatchers.IO) {
            runCatching {
                GameLauncher.launch(
                    versionId = versionId,
                    account = account,
                    settings = settings,
                    onLog = { line -> log(line) },
                )
            }.onSuccess { result ->
                ui {
                    gameRunning = true
                    status = "Minecraft chal raha hai"
                }
                scope.launch(Dispatchers.IO) {
                    val code = result.process.waitFor()
                    ui {
                        gameRunning = false
                        status = "Game band ho gaya (exit $code)"
                    }
                }
            }.onFailure { error ->
                ui {
                    gameRunning = false
                    status = "Launch fail: ${error.message}"
                }
                log("Launch error: ${error.message}")
            }
        }
    }

    fun openGameFolder() {
        runCatching { java.awt.Desktop.getDesktop().open(AppPaths.game) }
    }

    suspend fun pingStatus() = withContext(Dispatchers.IO) { AppPaths.gameSizeMb() }
}
