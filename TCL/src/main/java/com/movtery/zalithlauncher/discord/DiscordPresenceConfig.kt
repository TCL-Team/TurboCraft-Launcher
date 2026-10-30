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

/**
 * Discord Rich Presence 的全部默认值都集中在这一个文件里。
 *
 * 启动器设置里不再提供任何 Discord 选项：普通用户看不到、也改不了，
 * 想调整文案或按钮链接时只改这里即可（改完重新打包）。
 */
object DiscordPresenceConfig {
    /**
     * 是否显示 Discord Rich Presence。
     */
    const val ENABLED = true

    /**
     * Discord Application ID 兜底值。
     *
     * 构建时注入的 `DISCORD_APP_ID`（CI 里来自仓库 Secret）优先；
     * Secret 没配置时才会用这里的值，两者都为空则功能静默关闭。
     */
    const val APPLICATION_ID = "1565597159183753256"

    /**
     * 第二行文案（Discord 的 details）。留空则不显示这一行。
     */
    const val DETAILS = ""

    /**
     * 第三行文案（Discord 的 state）。留空则不显示这一行。
     */
    const val STATE = "Minecraft: Java Edition"

    /**
     * 第一个按钮：文字 + 链接（留空则不加这个按钮）。
     */
    const val PRIMARY_BUTTON_LABEL = "Download"
    const val PRIMARY_BUTTON_URL = "https://github.com/TCL-Team/TurboCraft-Launcher/releases/latest"

    /**
     * 第二个按钮：文字 + 链接（Discord 最多允许 2 个按钮）。
     */
    const val SECONDARY_BUTTON_LABEL = "GitHub"
    const val SECONDARY_BUTTON_URL = "https://github.com/TCL-Team/TurboCraft-Launcher"
}
