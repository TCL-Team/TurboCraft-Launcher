package git.artdeell.mojoexec;

/**
 * JNI names match MojoLauncher/mojoexec. Methods live in libmojoexec.so.
 */
public class MojoExec {
    private static boolean loaded;

    public static boolean ensureLoaded() {
        if (loaded) return true;
        try {
            System.loadLibrary("mojoexec");
            loaded = true;
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static native boolean prepareEgl(String eglPath, boolean useBypass, boolean useGles, int glesVersion);
    public static native void setDisplayParams(int width, int height, float hz);
    public static native void setUseTurnip(boolean enable);
    public static native void preloadVulkan();
    public static native void setNativeLibraryDir(String dir);
    public static native void setUseBigCoreAffinity(boolean enable);
}
