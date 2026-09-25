package org.lwjgl.openal;

public final class SOFTSystemEvents {
    private SOFTSystemEvents() {}

    public static int alcEventIsSupportedSOFT(int eventType, int deviceType) {
        return 0;
    }

    public static boolean alcEventControlSOFT(int[] types, boolean enable) {
        return false;
    }

    public static boolean alcEventControlSOFT(java.nio.IntBuffer types, boolean enable) {
        return false;
    }

    public static void alcEventCallbackSOFT(long callback, long userParam) {
    }
}
