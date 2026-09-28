package com.movtery.zalithlauncher.game.renderer.renderers

import com.movtery.zalithlauncher.game.renderer.RendererInterface

object FreedrenoRenderer : RendererInterface {
    override fun getRendererId(): String = "gallium_freedreno"

    override fun getUniqueIdentifier(): String = "1ad7249f-5784-4f00-bc72-174b3578ee46"

    override fun getRendererName(): String = "Freedreno (Adreno)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        buildMap {
            put("MESA_LOADER_DRIVER_OVERRIDE", "freedreno")
            put("GALLIUM_DRIVER", "freedreno")
            put("TU_DEBUG", "sysmem")
        }
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}
