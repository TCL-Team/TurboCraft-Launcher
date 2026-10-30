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

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.renderer.ConfigurableRenderer
import com.movtery.zalithlauncher.game.renderer.config.ConfigTarget
import com.movtery.zalithlauncher.game.renderer.config.rendererConfigSpec

object SimpleFPEWrapperRenderer : ConfigurableRenderer {
    override val configSpec = rendererConfigSpec {
        fixedEnv("LIBGL_ES" to "3")
        switch(
            id = "SFPEW_RELAXED_CONTEXT",
            title = R.string.renderer_config_sfpew_relaxed_context_title,
            description = R.string.renderer_config_sfpew_relaxed_context_desc,
            default = true,
            target = ConfigTarget.Environment("SFPEW_RELAXED_CONTEXT")
        )
    }

    override val legacyConfigKeys: List<String> = listOf("builtin:sfpew")

    override fun getRendererId(): String = "opengles3_sfpew"

    override fun getUniqueIdentifier(): String = "f3e4280b-db2e-42e2-95eb-671640b7b0eb"

    override fun getRendererName(): String = "SimpleFPEWrapper"

    override fun getRendererSummary(): String = "OpenGL 2.1 compatibility / 3.2+"

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libSimpleFPEWrapper.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
