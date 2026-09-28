package git.artdeell.mojoexec

/**
 * JNI must stay this package+name — symbols in libmojoexec.so are
 * Java_git_artdeell_mojoexec_MojoExec_*.
 *
 * Put libmojoexec.so from Mojo APK into TCL jniLibs/arm64-v8a.
 * This is only EGL preload. Mojo SDL / linkerhook still missing.
 */
object MojoExec {
    @Volatile
    private var loaded = false

    @JvmStatic
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("mojoexec")
            loaded = true
            true
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    external fun setNativeLibraryDir(dir: String)

    @JvmStatic
    external fun prepareEgl(
        eglPath: String,
        useBypass: Boolean,
        useGles: Boolean,
        glesVersion: Int
    ): Boolean

    @JvmStatic
    external fun setDisplayParams(width: Int, height: Int, hz: Float)
}
