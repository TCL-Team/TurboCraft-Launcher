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
import com.movtery.zalithlauncher.utils.logging.Logger
import java.lang.ref.WeakReference

/**
 * Discord Rich Presence (token-free).
 *
 * 启动器在前台时，通过 Discord Social SDK 向同设备上已登录的 Discord 客户端广播
 * “正在玩 TurboCraft Launcher”；不需要任何 Discord 账号令牌，也不涉及机器人。
 *
 * 所有文案、按钮和开关都在 [DiscordPresenceConfig] 里，启动器设置界面不暴露任何相关选项。
 * Application ID 优先取构建时注入的 [BuildConfig.DISCORD_APP_ID]（CI 从仓库 Secret 传入），
 * 没有时用配置文件的兜底值；两者都为空则功能静默关闭。
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

    /**
     * 当前生效的 Discord Application ID。
     *
     * 优先使用构建时注入的值（`DISCORD_APP_ID`，CI 里来自仓库 Secret），
     * 没有配置时回退到 [DiscordPresenceConfig.APPLICATION_ID]。
     * 两者都为空表示功能关闭。
     */
    fun effectiveApplicationId(): String = BuildConfig.DISCORD_APP_ID.trim()
        .ifBlank { DiscordPresenceConfig.APPLICATION_ID.trim() }

    fun isConfigured(): Boolean = effectiveApplicationId().isNotEmpty()

    /**
     * 在 Activity 创建时调用。[DiscordPresenceConfig.ENABLED] 关闭或没有 Application ID
     * 时什么都不做。
     */
    fun start(activity: Activity) {
        if (started || !DiscordPresenceConfig.ENABLED) return

        val id = effectiveApplicationId()
        if (id.isEmpty()) {
            Logger.warning(TAG, "Discord Rich Presence disabled: no application id configured.")
            return
        }

        runCatching {
            activityRef = WeakReference(activity)
            if (!DiscordPresenceBridge.prepare(activity)) return
            if (!DiscordPresenceBridge.init(id.toLongOrNull() ?: return)) return
            started = true
            pushPresence()
        }.onFailure {
            Logger.error(TAG, "Failed to start Discord Rich Presence.", it)
        }
    }

    /**
     * 重新推送一次状态（例如从后台回到前台后 Discord 才启动）。
     */
    fun refresh() {
        if (!DiscordPresenceConfig.ENABLED) return
        if (!started) {
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

    private fun pushPresence() {
        val details = DiscordPresenceConfig.DETAILS.trim()
        val state = DiscordPresenceConfig.STATE.trim()

        val labels = mutableListOf<String>()
        val urls = mutableListOf<String>()
        sanitizeUrl(DiscordPresenceConfig.PRIMARY_BUTTON_URL)?.let {
            labels += DiscordPresenceConfig.PRIMARY_BUTTON_LABEL.trim()
            urls += it
        }
        sanitizeUrl(DiscordPresenceConfig.SECONDARY_BUTTON_URL)?.let {
            labels += DiscordPresenceConfig.SECONDARY_BUTTON_LABEL.trim()
            urls += it
        }

        // Discord 没有显式标题时会显示成 “Game”，所以这里一定要带上启动器名称
        val title = DiscordPresenceConfig.TITLE.trim().ifBlank { BuildKeys.LAUNCHER_NAME }

        applicationId = effectiveApplicationId()
        DiscordPresenceBridge.updatePresence(
            name = title,
            details = limit(details, 128),
            state = limit(state, 128),
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
