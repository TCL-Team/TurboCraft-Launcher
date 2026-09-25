#include <jni.h>

/*
 * LWJGL 3.4.1 CallbackBridge.nativeNotifyLauncher(int, int[])
 * Called from org.lwjgl.sdl.SDLInit.SDL_Init before SDL_Init.
 * Returning true is enough for 26.3 to continue; SDL hooks in libexithook.so
 * already intercept SDL_InitSubSystem.
 */
JNIEXPORT jboolean JNICALL
Java_org_lwjgl_glfw_CallbackBridge_nativeNotifyLauncher(JNIEnv *env, jclass clazz,
                                                        jint type, jintArray action) {
    (void)env;
    (void)clazz;
    (void)type;
    (void)action;
    return JNI_TRUE;
}
