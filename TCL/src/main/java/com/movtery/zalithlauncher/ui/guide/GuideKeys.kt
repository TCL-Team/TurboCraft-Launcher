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

package com.movtery.zalithlauncher.ui.guide

import com.movtery.guide.GuideKey

/** 启动器各类引导 */
sealed interface GuideKeys : GuideKey {
    /** 一组引导（一条引导流的身份），用于跨层启动事件与进度存储 */
    sealed interface Keys {
        /** 该组引导的版本号，版本号提升时已看过旧版引导的用户会重新看到引导 */
        val version: Int
    }

    /** 主界面引导 */
    data object Main : Keys {
        override val version = 1

        sealed interface Step : GuideKey {
            /** 添加账号入口 */
            data object Account : Step
            /** 版本列表入口 */
            data object VersionList : Step
            /** 启动游戏按钮 */
            data object Launch : Step
            /** 游玩统计面板 */
            data object Stats : Step
            /** 侧边栏按钮组 */
            data object SideBar : Step
        }
    }

    /** 控制布局编辑器的引导 */
    data object Editor : Keys {
        override val version = 1

        sealed interface Step : GuideKey {
            /** 悬浮菜单 */
            data object MenuBall : Step
            /** 控件层列表 */
            data object LayerList : Step
            /** 创建控件层 */
            data object CreateLayer : Step
            /** 创建控件 */
            data object AddButtons : Step
            /** 创建控件样式 */
            data object AddStyles : Step
            /** 预览控制布局 */
            data object Preview : Step
            /** 保存 */
            data object Save : Step
        }
    }
}