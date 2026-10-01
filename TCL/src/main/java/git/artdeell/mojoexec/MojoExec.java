package git.artdeell.mojoexec;

/**
 * JNI names match MojoLauncher/mojoexec. The methods live in libmojoexec.so,
 * which is built from the mojoexec submodule. Do not call these on 26.2.
 */
public class MojoExec {
    static {
        System.loadLibrary("mojoexec");
    }

    public static native boolean prepareEgl(String eglPath, boolean useBypass, boolean useGles, int glesVersion);
    public static native void setDisplayParams(int width, int height, float hz);
    public static native void setUseTurnip(boolean enable);
    public static native void preloadVulkan();
    public static native void setNativeLibraryDir(String dir);
    public static native void setUseBigCoreAffinity(boolean enable);
}
