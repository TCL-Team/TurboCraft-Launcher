package com.movtery.zalithlauncher.game.launch

import android.util.Log
import git.artdeell.mojoexec.MojoExec

/**
 * New file only. Call Tcl26EglBootstrap.preload(eglPath, libDir) from your existing
 * dlopenEngine() after the renderer .so is loaded. Do not replace sdl_hook.c.
 *
 * libmojoexec.so must be in jniLibs/arm64-v8a (copied from Mojo APK).
 * This does not replace libSDL3.so. 26.3 still needs Mojo's SDL to take the handle.
 */
object Tcl26EglBootstrap {
    fun preload(eglPath: String, libDir: String) {
        if (!MojoExec.ensureLoaded()) {
            Log.w("TCL26", "libmojoexec.so missing")
            return
        }
        MojoExec.setNativeLibraryDir(libDir)
        val ok = MojoExec.prepareEgl(eglPath, false, true, 3)
        Log.i("TCL26", "prepareEgl $eglPath = $ok")
    }
}
