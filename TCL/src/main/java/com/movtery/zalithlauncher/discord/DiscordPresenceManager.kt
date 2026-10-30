/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.zalithlauncher.discord

import android.app.Activity
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import java.lang.ref.WeakReference

/**
 * Discord Rich Presence (token-free).
 *
 * 启动器在前台时，通过 Discord Social SDK 向同设备上已登录的 Discord 客户端广播
 * “正在玩 TurboCraft Launcher”；不需要任何 Discord 账号令牌，也不涉及机器人。
 *
 * Application ID 优先取设置里的覆盖值，其次取构建时注入的 [BuildConfig.DISCORD_APP_ID]
 * （CI 从仓库 Secret 传入）。两者都为空时功能静默关闭。
 */
object DiscordPresenceManager {
    private const val TAG = "DiscordPresence"

    /** Discord 最多允许 2 个按钮。 */
    private const val MAX_BUTTONS = 2

    /** Discord 要求的时间戳（毫秒）。 */
    private val startedAt = System.currentTimeMillis()

    private var started = false
    private var applicationId = ""
    private var activityRef: WeakReference<Activity>? = null

    private val defaultDownloadUrl: String
        get() = BuildKeys.URL_HOME.trimEnd('/') + "/releases/latest"

    /**
     * 当前生效的 Discord Application ID（设置覆盖 > 构建注入），为空表示未配置。
     */
    fun effectiveApplicationId(): String =
        AllSettings.discordApplicationIdOverride.getValue().trim().ifBlank {
            BuildConfig.DISCORD_APP_ID.trim()
        }

    fun isConfigured(): Boolean = effectiveApplicationId().isNotEmpty()

    /**
     * 在 Activity 创建时调用。功能关闭或未配置 Application ID 时什么都不做。
     */
    fun start(activity: Activity) {
        // 即使当前关闭，也记住 Activity，方便用户稍后在设置里开启时立即生效
        activityRef = WeakReference(activity)
        if (started) return
        if (!AllSettings.discordRichPresence.getValue()) return

        val id = effectiveApplicationId()
        if (id.isEmpty()) {
            Logger.warning(TAG, "Discord Rich Presence disabled: no application id configured.")
            return
        }

        runCatching {
            if (!DiscordPresenceBridge.prepare(activity)) return
            if (!DiscordPresenceBridge.init(id.toLongOrNull() ?: return)) return
            started = true
            pushPresence()
        }.onFailure {
            Logger.error(TAG, "Failed to start Discord Rich Presence.", it)
        }
    }

    /**
     * 设置变更后重新推送一次（开关、文案、下载地址）。
     */
    fun refresh() {
        if (!started) {
            activityRef?.get()?.let { start(it) }
            return
        }
        if (!AllSettings.discordRichPresence.getValue()) {
            stop()
            return
        }
        val id = effectiveApplicationId()
        if (id.isEmpty() || id != applicationId) {
            // Application ID 变了：只能重开客户端。
            shutdown()
            activityRef?.get()?.let { start(it) }
            return
        }
        pushPresence()
    }

    /**
     * 关闭启动器时清理正在显示的 Rich Presence。
     */
    fun stop() {
        if (!started) return
        runCatching { DiscordPresenceBridge.clear() }
        started = false
    }

    private fun shutdown() {
        runCatching {
            if (started) DiscordPresenceBridge.clear()
            DiscordPresenceBridge.shutdown()
        }
        started = false
    }

    private fun pushPresence() {
        val details = AllSettings.discordCustomDetails.getValue().trim()
            .ifBlank { "TurboCraft Launcher ${BuildConfig.VERSION_NAME}" }
        val downloadUrl = AllSettings.discordCustomDownloadUrl.getValue().trim()
            .ifBlank { defaultDownloadUrl }

        val labels = mutableListOf<String>()
        val urls = mutableListOf<String>()
        sanitizeUrl(downloadUrl)?.let {
            labels += "Download"
            urls += it
        }
        sanitizeUrl(BuildKeys.URL_HOME)?.let {
            labels += "GitHub"
            urls += it
        }

        applicationId = effectiveApplicationId()
        DiscordPresenceBridge.updatePresence(
            name = BuildKeys.LAUNCHER_NAME,
            details = limit(details, 128),
            state = limit("Minecraft Java Edition", 128),
            activityType = 0, // Playing
            startTimestampMs = startedAt,
            largeImage = "",
            largeText = "",
            smallImage = "",
            smallText = "",
            buttonLabels = labels.take(MAX_BUTTONS).toTypedArray(),
            buttonUrls = urls.take(MAX_BUTTONS).toTypedArray(),
        )
    }

    private fun limit(value: String, maximum: Int): String {
        if (value.isEmpty()) return ""
        val count = value.codePointCount(0, value.length)
        if (count <= maximum) return value
        return value.substring(0, value.offsetByCodePoints(0, maximum))
    }

    private fun sanitizeUrl(value: String): String? {
        val candidate = value.trim().let {
            if (it.isEmpty()) return null
            if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it"
        }
        if (candidate.length > 512) return null
        return runCatching { java.net.URI(candidate) }
            .getOrNull()
            ?.takeIf { !it.host.isNullOrBlank() }
            ?.let { candidate }
    }
}
