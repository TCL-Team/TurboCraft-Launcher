/*
 * TurboCraft: ZalithLauncher 2 style versioned LWJGL components.
 * 3.3.3 is used by older Minecraft versions.
 * 3.4.1 is used by Minecraft 26.3+ (LWJGL >= 3.4.1, includes SDL).
 */
package com.movtery.zalithlauncher.components

import com.movtery.zalithlauncher.R

enum class Components(val component: String, val displayName: String, val summary: Int) {
    AUTH_LIBS("auth_libs", "authlib-injector", R.string.unpack_screen_authlib_injector),
    CACIOCAVALLO("caciocavallo", "caciocavallo", R.string.unpack_screen_cacio),
    CACIOCAVALLO17("caciocavallo17", "caciocavallo 17", R.string.unpack_screen_cacio),
    LWJGL3("lwjgl3", "LWJGL 3.3.6", R.string.unpack_screen_lwjgl),
    LWJGL_333("lwjgl/3.3.3", "LWJGL 3.3.3", R.string.unpack_screen_lwjgl),
    LWJGL_341("lwjgl/3.4.1", "LWJGL 3.4.1", R.string.unpack_screen_lwjgl),
    LAUNCHER("launcher", "Launcher Components", R.string.unpack_screen_launcher)
}
