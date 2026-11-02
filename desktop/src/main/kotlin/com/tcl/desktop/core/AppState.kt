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

enum class Screen { HOME, VERSIONS, INSTALL, MODS, ACCOUNTS, SETTINGS, LOGS }

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
    var status by mutableStateOf("Ready")
    var statusIsError by mutableStateOf(false)
        private set
    var progress by mutableStateOf<InstallProgress?>(null)
    var busy by mutableStateOf(false)
    var gameRunning by mutableStateOf(false)
    var deviceCode by mutableStateOf<String?>(null)
    var remoteLoading by mutableStateOf(false)
        private set

    // ── startup (Android jaisa: pehle components, phir UI) ──────────────────
    var bootstrapped by mutableStateOf(false)
        private set
    var bootstrapSteps by mutableStateOf<List<BootstrapStep>>(emptyList())
        private set

    // ── loader picker ──────────────────────────────────────────────────────
    var loaderTargetVersion by mutableStateOf<String?>(null)
    var loaderType by mutableStateOf(LoaderType.VANILLA)
    var loaderVersionList by mutableStateOf<List<LoaderVersion>>(emptyList())
    var loaderVersionsLoading by mutableStateOf(false)
    var selectedLoaderVersion by mutableStateOf<String?>(null)

    // ── Modrinth browser ───────────────────────────────────────────────────
    var browseQuery by mutableStateOf("")
    var browseType by mutableStateOf("mod")
    var browseResults by mutableStateOf<List<ProjectHit>>(emptyList())
    var browseLoading by mutableStateOf(false)
    var browseError by mutableStateOf<String?>(null)

    val logs = mutableStateListOf<String>()

    val msClientId: String
        get() = settings.msClientId.takeIf { it.isNotBlank() } ?: MicrosoftAuth.DEFAULT_CLIENT_ID

    val selectedVersion: String get() = settings.selectedVersion

    private fun ui(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    private fun setStatus(message: String, error: Boolean = false) {
        status = message
        statusIsError = error
    }

    private fun fail(prefix: String, error: Throwable) {
        val message = when (error) {
            is NetworkException -> error.message ?: prefix
            else -> error.message ?: error.javaClass.simpleName
        }
        ui { setStatus("$prefix: $message", error = true) }
        log("$prefix: ${error.message ?: error.javaClass.name}")
    }

    fun log(line: String) {
        ui {
            logs += line
            if (logs.size > 500) logs.removeRange(0, logs.size - 500)
        }
    }

    /** Launcher start par components (Java etc.) ka setup */
    fun startBootstrap() {
        if (bootstrapSteps.isNotEmpty()) return
        scope.launch {
            runCatching {
                Bootstrap.run(javaMajor = 21) { steps ->
                    scope.launch(Dispatchers.Main) { bootstrapSteps = steps }
                }
            }
            withContext(Dispatchers.Main) {
                bootstrapped = true
                refreshInstalled()
                loadRemoteVersions()
            }
        }
    }

    // ── loaders ────────────────────────────────────────────────────────────

    fun openLoaderPicker(baseVersion: String) {
        loaderTargetVersion = baseVersion
        loaderType = LoaderType.FABRIC
        selectedLoaderVersion = null
        loadLoaderVersions()
    }

    fun closeLoaderPicker() {
        loaderTargetVersion = null
        loaderVersionList = emptyList()
        selectedLoaderVersion = null
    }

    fun selectLoader(type: LoaderType, loaderVersion: String? = null) {
        loaderType = type
        selectedLoaderVersion = loaderVersion
        loadLoaderVersions()
    }

    private fun loadLoaderVersions() {
        val target = loaderTargetVersion ?: return
        if (loaderType == LoaderType.VANILLA || loaderType == LoaderType.OPTIFINE) {
            loaderVersionList = emptyList()
            return
        }
        loaderVersionsLoading = true
        scope.launch(Dispatchers.IO) {
            runCatching { Loaders.list(loaderType, target) }
                .onSuccess { list ->
                    ui {
                        loaderVersionList = list
                        selectedLoaderVersion = list.firstOrNull()?.version
                        loaderVersionsLoading = false
                    }
                }
                .onFailure { error ->
                    ui { loaderVersionsLoading = false }
                    log("Loader list failed: ${error.message}")
                }
        }
    }

    /** Loader picker se install — vanilla ho to seedha version install */
    fun installWithLoader() {
        val target = loaderTargetVersion ?: return
        if (busy) return
        if (loaderType == LoaderType.VANILLA) {
            closeLoaderPicker()
            installVersion(target)
            return
        }
        busy = true
        progress = InstallProgress(stage = "Preparing ${loaderType.label}…")
        val type = loaderType
        val version = selectedLoaderVersion
        closeLoaderPicker()
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (!AppPaths.versionJson(target).exists()) {
                    installer.install(target) { p -> ui { progress = p } }
                }
                Loaders.install(type, target, version) { p -> ui { progress = p } }
            }.onSuccess { id ->
                ui {
                    busy = false
                    progress = null
                    refreshInstalled()
                    selectVersion(id)
                    setStatus("${type.label} installed · $id")
                }
            }.onFailure { error ->
                ui {
                    busy = false
                    progress = null
                }
                fail("${type.label} install failed", error)
            }
        }
    }

    // ── Modrinth ───────────────────────────────────────────────────────────

    fun searchProjects(query: String = browseQuery, type: String = browseType) {
        browseQuery = query
        browseType = type
        browseLoading = true
        browseError = null
        val gameVersion = selectedVersion.takeIf { it.isNotBlank() }?.let { id ->
            Packs.read(id)?.mcVersion ?: VersionResolver.resolve(id).let { resolved ->
                runCatching { VersionResolver.resolve(id).jarId }.getOrNull()
            }
        }
        val loader = selectedVersion.takeIf { it.isNotBlank() }?.let { id ->
            when {
                id.startsWith("fabric") || id.startsWith("pack-") -> "fabric"
                id.startsWith("quilt") -> "quilt"
                id.startsWith("neoforge") -> "neoforge"
                id.startsWith("forge") -> "forge"
                else -> null
            }
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                Modrinth.search(
                    query = query,
                    projectType = type,
                    gameVersion = if (type == "mod") gameVersion else null,
                    loader = if (type == "mod") loader else null
                )
            }.onSuccess { hits -> ui { browseResults = hits; browseLoading = false } }
                .onFailure { error ->
                    ui {
                        browseLoading = false
                        browseError = error.message
                    }
                }
        }
    }

    /** Mod ka compatible version dhundh kar mods folder me daalta hai */
    fun installMod(hit: ProjectHit) {
        if (busy) return
        val versionId = selectedVersion
        busy = true
        setStatus("Looking for a compatible file…")
        scope.launch(Dispatchers.IO) {
            runCatching {
                val meta = Packs.read(versionId)
                val mc = meta?.mcVersion ?: VersionResolver.resolve(versionId).jarId
                val loader = when {
                    meta?.loader?.isNotBlank() == true -> meta.loader
                    versionId.startsWith("quilt") -> "quilt"
                    versionId.startsWith("neoforge") -> "neoforge"
                    versionId.startsWith("forge") -> "forge"
                    versionId.startsWith("fabric") || versionId.startsWith("pack-") -> "fabric"
                    else -> null
                }
                val versions = Modrinth.versions(hit.id, mc, loader)
                val chosen = versions.firstOrNull()
                    ?: Modrinth.versions(hit.id).firstOrNull()
                    ?: error("No file for Minecraft $mc${loader?.let { " / $it" } ?: ""}")
                Modrinth.installMod(
                    version = chosen,
                    targetModsDir = AppPaths.modsDirFor(versionId),
                    gameVersion = mc,
                    loader = loader,
                    onStatus = { message -> ui { setStatus(message) } }
                )
            }.onSuccess { count ->
                ui {
                    busy = false
                    setStatus("$count file(s) added to ${Mods.folderLabel(selectedVersion)}")
                }
            }.onFailure { error ->
                ui { busy = false }
                fail("Mod install failed", error)
            }
        }
    }

    /** Modrinth modpack install (naya isolated version banata hai) */
    fun installModpack(hit: ProjectHit) {
        if (busy) return
        busy = true
        setStatus("Fetching ${hit.title}…")
        scope.launch(Dispatchers.IO) {
            runCatching {
                val versions = Modrinth.versions(hit.id)
                val chosen = versions.firstOrNull() ?: error("No versions available for this modpack")
                ModpackInstaller.install(hit.id, chosen, onProgress = { p -> ui { progress = p } })
            }.onSuccess { packId ->
                ui {
                    busy = false
                    progress = null
                    refreshInstalled()
                    selectVersion(packId)
                    setStatus("Modpack installed · ${Packs.displayName(packId)}")
                }
            }.onFailure { error ->
                ui {
                    busy = false
                    progress = null
                }
                fail("Modpack install failed", error)
            }
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

    /** Minecraft version list (manifest) */
    fun loadRemoteVersions(force: Boolean = false) {
        if (remoteLoading || (remoteVersions.isNotEmpty() && !force)) return
        remoteLoading = true
        setStatus("Loading Minecraft versions…")
        scope.launch(Dispatchers.IO) {
            runCatching { installer.listVersions() }
                .onSuccess { manifest ->
                    ui {
                        remoteVersions = manifest.versions
                        remoteLoading = false
                        setStatus("${manifest.versions.size} versions available")
                    }
                }
                .onFailure { error ->
                    ui { remoteLoading = false }
                    fail("Couldn't load versions", error)
                }
        }
    }

    /** Sirf install (koi launch nahi) */
    fun installVersion(versionId: String, playAfter: Boolean = false) {
        if (busy) return
        busy = true
        progress = InstallProgress(stage = "Preparing…")
        scope.launch(Dispatchers.IO) {
            runCatching {
                installer.install(versionId) { p -> ui { progress = p } }
            }.onSuccess {
                ui {
                    busy = false
                    progress = null
                    refreshInstalled()
                    selectVersion(versionId)
                    setStatus("$versionId installed")
                }
                // user ne Play dabaya tha to install ke turant baad launch
                if (playAfter) launchGame()
            }.onFailure { error ->
                ui {
                    busy = false
                    progress = null
                }
                fail("Install failed", error)
            }
        }
    }

    /** Play button: version install nahi hai to pehle install, phir launch */
    fun play(versionId: String = selectedVersion.ifBlank { installed.firstOrNull().orEmpty() }) {
        if (versionId.isBlank()) {
            setStatus("Pick a version first", error = true)
            screen = Screen.INSTALL
            return
        }
        selectVersion(versionId)
        if (versionId !in installed) {
            setStatus("Installing $versionId, then launching…")
            installVersion(versionId, playAfter = true)
        } else {
            launchGame()
        }
    }

    /** Vanilla version ke upar Fabric loader install karta hai */
    fun installFabric(baseVersion: String) {
        if (busy) return
        busy = true
        progress = InstallProgress(stage = "Looking up Fabric loader…")
        scope.launch(Dispatchers.IO) {
            runCatching {
                FabricInstaller.install(baseVersion, onProgress = { p -> ui { progress = p } })
            }.onSuccess { id ->
                ui {
                    busy = false
                    progress = null
                    refreshInstalled()
                    selectVersion(id)
                    setStatus("Fabric ready · $id")
                }
            }.onFailure { error ->
                ui {
                    busy = false
                    progress = null
                }
                fail("Fabric install failed", error)
            }
        }
    }

    fun addOfflineAccount(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val account = Account(
            type = AccountType.OFFLINE,
            name = trimmed,
            uuid = Accounts.offlineUuid(trimmed),
            accessToken = "0"
        )
        accounts = accounts.filterNot { it.name == account.name && it.type == AccountType.OFFLINE } + account
        Accounts.save(accounts)
        selectAccount(account)
        setStatus("Offline account added: ${account.name}")
    }

    fun removeAccount(account: Account) {
        accounts = accounts.filterNot { it.uuid == account.uuid }
        Accounts.save(accounts)
        if (selectedAccount?.uuid == account.uuid) {
            selectedAccount = accounts.firstOrNull()
        }
    }

    fun startMicrosoftLogin(useDeviceCode: Boolean = false) {
        if (busy) return
        busy = true
        setStatus("Starting Microsoft sign-in…")
        val clientId = msClientId
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (useDeviceCode) {
                    val info = MicrosoftAuth.requestDeviceCode(clientId)
                    ui {
                        deviceCode = info.userCode
                        setStatus("Enter code ${info.userCode} at ${info.verificationUri}")
                    }
                    runCatching {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(info.verificationUri))
                    }
                    MicrosoftAuth.signInWithDeviceCode(info, clientId) { message -> ui { setStatus(message) } }
                } else {
                    MicrosoftAuth.signInWithBrowser(clientId, onStatus = { message -> ui { setStatus(message) } })
                }
            }.onSuccess { account ->
                ui {
                    busy = false
                    deviceCode = null
                    accounts = (accounts.filterNot { it.uuid == account.uuid } + account)
                    Accounts.save(accounts)
                    selectAccount(account)
                    setStatus("Signed in as ${account.name}")
                }
            }.onFailure { error ->
                ui {
                    busy = false
                    deviceCode = null
                }
                fail("Sign-in failed", error)
            }
        }
    }

    fun launchGame() {
        val account = selectedAccount ?: run {
            setStatus("Add an account first", error = true)
            screen = Screen.ACCOUNTS
            return
        }
        if (gameRunning) {
            setStatus("Minecraft is already running")
            return
        }
        val versionId = selectedVersion.ifBlank { installed.firstOrNull().orEmpty() }
        if (versionId.isBlank()) {
            setStatus("Install a version first", error = true)
            screen = Screen.INSTALL
            return
        }

        setStatus("Launching $versionId…")
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
                    setStatus("Minecraft is running")
                }
                scope.launch(Dispatchers.IO) {
                    val code = result.process.waitFor()
                    ui {
                        gameRunning = false
                        setStatus("Minecraft closed (exit code $code)")
                    }
                }
            }.onFailure { error ->
                ui { gameRunning = false }
                fail("Launch failed", error)
            }
        }
    }

    fun openGameFolder() {
        runCatching { java.awt.Desktop.getDesktop().open(AppPaths.game) }
    }

    fun openLogsFolder() {
        runCatching { java.awt.Desktop.getDesktop().open(AppPaths.logs) }
    }

    suspend fun pingStatus() = withContext(Dispatchers.IO) { AppPaths.gameSizeMb() }
}
