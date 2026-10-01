package com.movtery.zalithlauncher.game.sdl

import android.content.Context
import android.util.Log
import git.artdeell.mojoexec.MojoExec

/**
 * 26.3 only. Real Mojo EGL setup. Never sets SDL_VIDEO_DRIVER=dummy.
 * 26.2 must not call this.
 */
object Mc26Mojo {
    private const val TAG = "Mc26Mojo"

    @JvmStatic
    fun prepare(context: Context, versionId: String?, width: Int, height: Int) {
        if (!Mc26Gate.needsSdlWindow(versionId)) return
        val dir = context.applicationInfo.nativeLibraryDir
        MojoExec.setNativeLibraryDir(dir)
        val w = if (width > 0) width else 1280
        val h = if (height > 0) height else 720
        MojoExec.setDisplayParams(w, h, 60f)
        val egl = "$dir/libltw.so"
        val ok = MojoExec.prepareEgl(egl, true, true, 3)
        Log.i(TAG, "prepareEgl $egl -> $ok (${w}x$h) version=$versionId")
    }
}
