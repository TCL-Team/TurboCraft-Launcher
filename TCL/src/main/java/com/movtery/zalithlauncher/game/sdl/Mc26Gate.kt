package com.movtery.zalithlauncher.game.sdl

/**
 * 26.3+ only. 26.2 and below stay on the existing GLFW path.
 * Minecraft 26.3 moved the window from GLFW to SDL3. Do not send those
 * versions through SDLActivity.nativeSetupJNI(); that SIGSEGVs libart.
 */
object Mc26Gate {
    @JvmStatic
    fun needsSdlWindow(versionId: String?): Boolean {
        if (versionId.isNullOrBlank()) return false
        val head = versionId.trim().substringBefore(' ').substringBefore('-')
        val parts = head.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major > 26 || (major == 26 && minor >= 3)
    }
}
