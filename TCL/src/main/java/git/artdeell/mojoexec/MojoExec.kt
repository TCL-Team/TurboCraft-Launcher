package git.artdeell.mojoexec

/** New file. JNI names must match libmojoexec.so from Mojo APK. */
object MojoExec {
    private var loaded = false

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

    @JvmStatic external fun setNativeLibraryDir(dir: String)
    @JvmStatic external fun prepareEgl(eglPath: String, useBypass: Boolean, useGles: Boolean, glesVersion: Int): Boolean
    @JvmStatic external fun setDisplayParams(width: Int, height: Int, hz: Float)
}
