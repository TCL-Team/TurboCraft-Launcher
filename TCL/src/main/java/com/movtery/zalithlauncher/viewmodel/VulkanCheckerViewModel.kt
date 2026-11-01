/*
 * Zalith Launcher 2
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.viewmodel

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import com.movtery.zalithlauncher.game.plugin.driver.Driver
import com.movtery.zalithlauncher.game.plugin.driver.DriverPluginManager
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.utils.isLowerVer
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.launcherMMKV
import com.movtery.zalithlauncher.ui.vulkan_checker.VCOperation
import com.movtery.zalithlauncher.utils.GSON
import com.movtery.zalithlauncher.utils.device.VulkanCapabilities
import com.movtery.zalithlauncher.utils.device.VulkanChecker
import com.movtery.zalithlauncher.utils.device.VulkanRequirements
import com.movtery.zalithlauncher.utils.device.normalizeMcVersion
import com.movtery.zalithlauncher.utils.device.profileSupport
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private const val TAG = "VulkanCheckerViewModel"
private const val KEY_VULKAN_CHECK_RECORD = "vulkanCheckRecord"

@Keep
data class VulkanCheckRecord(
    val useTurnip: Boolean,
    val driverPath: String,
    /** 设备可支持的 Minecraft 版本范围，[Range.until] 为排他上界，null 表示无上界 */
    val supportedRanges: List<Range> = emptyList(),
    val version: Int = 0,
) {
    @Keep
    data class Range(
        val since: String,
        val until: String?
    )

    /** 判断指定 Minecraft 版本是否落在已支持的版本范围内 */
    fun isSupported(mcVersion: String): Boolean {
        return supportedRanges.any { range ->
            !mcVersion.isLowerVer(range.since) &&
                    (range.until == null || mcVersion.isLowerVer(range.until))
        }
    }
}

/**
 * Vulkan 检测缓存：判断“上次检测的结果”在当前驱动状态下是否仍然适用于某个版本。
 *
 * 供启动流程使用：用户强制选择 Vulkan、但设备/驱动实际上拿不到 Vulkan 设备时，
 * 游戏会直接崩溃（Minecraft 26.4+ 的 renderpearl 后端拿不到设备就会抛
 * BackendCreationException）。这种情况下需要退回 OpenGL，而不是把 Vulkan 写进游戏设置。
 */
object VulkanCheckCache {
    /**
     * @return 当前驱动状态下，该版本是否确定可以使用 Vulkan
     */
    fun isSupported(version: Version): Boolean {
        val record = load() ?: return false
        if (record.version != VulkanRequirements.VULKAN_REQUIREMENTS_VERSION) return false

        val driver = DriverPluginManager.getDriver(version.getDriver())
        val useTurnip = !driver.isLauncher
        val path = if (useTurnip) driver.path else ""
        if (record.useTurnip != useTurnip || record.driverPath != path) return false

        val mcVersion = version.getVersionInfo()?.minecraftVersion
            ?.let { normalizeMcVersion(it) }
            ?: return false
        return record.isSupported(mcVersion)
    }

    private fun load(): VulkanCheckRecord? {
        val json = launcherMMKV().getString(KEY_VULKAN_CHECK_RECORD, null) ?: return null
        return runCatching {
            GSON.fromJson(json, VulkanCheckRecord::class.java)
        }.getOrNull()
    }
}

class VulkanCheckerViewModel: ViewModel() {
    private val _vcOperation = MutableStateFlow<VCOperation>(VCOperation.None)
    val vcOperation = _vcOperation.asStateFlow()

    private val mutex = Mutex()

    var vulkanCheckerCont: (Continuation<Unit>)? = null
        private set

    fun changeOperation(operation: VCOperation) {
        _vcOperation.update { operation }
    }

    suspend fun waitForVulkanChecker(version: Version) {
        suspendCancellableCoroutine { cont ->
            vulkanCheckerCont = cont
            changeOperation(VCOperation.Tip(version))

            cont.invokeOnCancellation {
                vulkanCheckerCont = null
            }
        }
    }

    fun resumeCont() {
        vulkanCheckerCont?.resume(Unit)
        vulkanCheckerCont = null
    }

    suspend fun check(version: Version): Pair<VulkanCapabilities?, Boolean> {
        return mutex.withLock {
            val driver = DriverPluginManager.getDriver(version.getDriver())
            val useTurnip = !driver.isLauncher
            val capabilities = doCheck(useTurnip, driver)
            saveRecord(
                VulkanCheckRecord(
                    useTurnip = useTurnip,
                    driverPath = driverPath(useTurnip, driver),
                    supportedRanges = capabilities?.profileSupport()
                        ?.filter { it.supported }
                        ?.map { VulkanCheckRecord.Range(since = it.since, until = it.until) }
                        ?: emptyList(),
                    version = VulkanRequirements.VULKAN_REQUIREMENTS_VERSION
                )
            )
            capabilities to useTurnip
        }
    }

    suspend fun ensureSupported(version: Version): Boolean {
        val driver = DriverPluginManager.getDriver(version.getDriver())
        val useTurnip = !driver.isLauncher
        val mcVersion = version.getVersionInfo()?.minecraftVersion?.let(::normalizeMcVersion)
        val path = driverPath(useTurnip, driver)

        loadRecord()?.takeIf { last ->
            //判断是否使用当前版本的检查器进行检测，版本不一致则数据失效重新检查
            last.version == VulkanRequirements.VULKAN_REQUIREMENTS_VERSION &&
            //判断Turnip环境是否不一致，不一致则数据失效重新检查
            last.useTurnip == useTurnip && last.driverPath == path
        }?.let {
            //同一驱动状态下设备能力不变，直接按已支持的版本范围判定
            return mcVersion != null && it.isSupported(mcVersion)
        }

        //记录不存在或驱动状态不一致时走完整的检测流程，检测完成后结果已保存
        waitForVulkanChecker(version)
        val record = loadRecord() ?: return false
        return mcVersion != null && record.isSupported(mcVersion)
    }

    private fun driverPath(useTurnip: Boolean, driver: Driver): String {
        return if (useTurnip) driver.path else ""
    }

    private suspend fun doCheck(useTurnip: Boolean, driver: Driver): VulkanCapabilities? {
        return withContext(Dispatchers.IO) {
            if (useTurnip) {
                val tempDir = File(PathManager.DIR_CACHE, "vulkan_temp")
                VulkanChecker.checkCapabilities(null, driver.path, tempDir.absolutePath)
            } else {
                VulkanChecker.checkCapabilities(null, null, null)
            }
        }
    }

    private fun loadRecord(): VulkanCheckRecord? {
        val json = launcherMMKV().getString(KEY_VULKAN_CHECK_RECORD, null) ?: return null
        return runCatching {
            GSON.fromJson(json, VulkanCheckRecord::class.java)
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to read vulkan check record", e)
        }.getOrNull()
    }

    private fun saveRecord(record: VulkanCheckRecord) {
        runCatching {
            launcherMMKV()
                .putString(KEY_VULKAN_CHECK_RECORD, GSON.toJson(record))
                .apply()
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to save vulkan check record", e)
        }
    }
}