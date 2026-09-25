//
// Created by maks on 06.01.2025.
//

#include <android/api-level.h>
#include <android/log.h>
#include <jni.h>

#include <environ/environ.h>

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>

extern void* maybe_load_vulkan();

/**
 * Minecraft 26.1+ probes ALC_SOFT_system_events through LWJGL.
 * Android OpenAL Soft shipped in the APK does not export those symbols,
 * so LWJGL leaves the pointer NULL and Checks.check() NPEs.
 * Report "not supported" instead of crashing.
 */
static int alcEventIsSupportedSOFT_stub(int eventType, int deviceType) {
    (void)eventType;
    (void)deviceType;
    return 0; /* ALC_FALSE */
}

static int alcEventControlSOFT_stub(int count, const int *types, int enable) {
    (void)count;
    (void)types;
    (void)enable;
    return 0;
}

static void alcEventCallbackSOFT_stub(void *callback, void *user) {
    (void)callback;
    (void)user;
}

/**
 * Basically a verbatim implementation of ndlopen(), found at
 * https://github.com/PojavLauncherTeam/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/generated/c/linux/org_lwjgl_system_linux_DynamicLinkLoader.c#L11
 * but with our own additions for stuff like vulkanmod.
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                            __attribute__((unused)) jclass class,
                            jlong filename_ptr,
                            jint jmode) {
    const char* filename = (const char*) filename_ptr;

    // Oveeride vulkan loading to let us load vulkan ourselves
    if(strstr(filename, "libvulkan.so") == filename) {
        printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
        return (jlong) maybe_load_vulkan();
    }

    // This hook also serves the task of mitigating a bug: the idea is that since, on Android 10 and
    // earlier, the linker doesn't really do namespace nesting.
    // It is not a problem as most of the libraries are in the launcher path, but when you try to run
    // VulkanMod which loads shaderc outside of the default jni libs directory through this method,
    // it can't load it because the path is not in the allowed paths for the anonymous namesapce.
    // This method fixes the issue by being in libpojavexec, and thus being in the classloader namespace

    int mode = (int)jmode;
    return (jlong) dlopen(filename, mode);
}

static jlong ndlsym_compat(__attribute__((unused)) JNIEnv *env,
                           __attribute__((unused)) jclass class,
                           jlong handle,
                           jlong name_ptr) {
    const char *name = (const char *) name_ptr;
    void *real = dlsym((void *) handle, name);
    if (real != NULL) return (jlong) real;
    if (name == NULL) return 0;
    if (strcmp(name, "alcEventIsSupportedSOFT") == 0) {
        return (jlong) (void *) alcEventIsSupportedSOFT_stub;
    }
    if (strcmp(name, "alcEventControlSOFT") == 0) {
        return (jlong) (void *) alcEventControlSOFT_stub;
    }
    if (strcmp(name, "alcEventCallbackSOFT") == 0) {
        return (jlong) (void *) alcEventCallbackSOFT_stub;
    }
    return 0;
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */
void installLwjglDlopenHook() {
    __android_log_print(ANDROID_LOG_INFO, "LwjglLinkerHook", "Installing LWJGL dlopen() hook");
    JNIEnv* env = pojav_environ->runtimeJNIEnvPtr_JRE;
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "LwjglLinkerHook", "Failed to find the target class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod methods[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym", "(JJ)J", &ndlsym_compat}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, methods, 2) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "LwjglLinkerHook", "Failed to register the hooked method");
        (*env)->ExceptionClear(env);
    }
}
