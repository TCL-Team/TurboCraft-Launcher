/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.settings.MobileGluesConfig
import java.io.File

object MobileGluesRenderer : RendererInterface {
    override fun getRendererId(): String = "mobileglues"

    override fun getUniqueIdentifier(): String = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

    override fun getRendererName(): String = "MobileGlues"

    override fun getRendererSummary(): String = "GL on top of OpenGL ES"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val dir = File(MobileGluesConfig.CONFIG_FILE_PATH).parentFile
            ?: File("/sdcard/MG")
        dir.mkdirs()
        val cfg = File(dir, "config.json")
        val existing = runCatching { cfg.readText() }.getOrNull().orEmpty()
        if (!existing.contains("\"ignoreError\"")) {
            cfg.writeText(
                """{"ignoreError":1,"enableNoError":3,"enableExtComputeShader":1,"enableExtDirectStateAccess":1,"enableANGLE":0}"""
            )
        }
        mapOf("MG_DIR_PATH" to dir.absolutePath)
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libMobileGlues.so"
}
