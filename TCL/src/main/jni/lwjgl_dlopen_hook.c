//
// Created by maks on 06.01.2025.
//

#include <android/api-level.h>
#include <android/log.h>
#include <jni.h>

#include <environ/environ.h>

#include <bytehook.h>
#include <dlfcn.h>
#include <string.h>
#include <stdint.h>
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

/* LWJGL 3.4.1 SDLVideo requires newer SDL3 symbols than TCL's libSDL3. */
static int sdl_stub_true(void) { return 1; }
static int sdl_stub_zero(void) { return 0; }
static void sdl_stub_void(void) {}
static void *sdl_stub_null(void) { return NULL; }
static float sdl_stub_onef(void) { return 1.0f; }
static const char *sdl_stub_dummy(void) { return "dummy"; }
static const char *sdl_stub_android(void) { return "Android"; }
static uint32_t sdl_stub_display_ids[2] = {1, 0};
static uint32_t *sdl_stub_get_displays(int *count) {
    if (count) *count = 1;
    return sdl_stub_display_ids;
}
static uint32_t sdl_stub_primary_display(void) { return 1; }
static int sdl_stub_display_bounds(uint32_t id, int *rect) {
    (void)id;
    if (rect) { rect[0] = 0; rect[1] = 0; rect[2] = 1688; rect[3] = 756; }
    return 1;
}

static void *resolve_missing_sdl(const char *name) {
    if (name == NULL || strncmp(name, "SDL_", 4) != 0) return NULL;
    if (strcmp(name, "SDL_SetWindowFillDocument") == 0) return (void *)sdl_stub_true;
    if (strcmp(name, "SDL_GetDisplays") == 0) return (void *)sdl_stub_get_displays;
    if (strcmp(name, "SDL_GetPrimaryDisplay") == 0) return (void *)sdl_stub_primary_display;
    if (strcmp(name, "SDL_GetDisplayBounds") == 0) return (void *)sdl_stub_display_bounds;
    if (strcmp(name, "SDL_GetDisplayUsableBounds") == 0) return (void *)sdl_stub_display_bounds;
    if (strcmp(name, "SDL_GetNumVideoDrivers") == 0) return (void *)sdl_stub_true;
    if (strcmp(name, "SDL_GetVideoDriver") == 0) return (void *)sdl_stub_dummy;
    if (strcmp(name, "SDL_GetCurrentVideoDriver") == 0) return (void *)sdl_stub_dummy;
    if (strcmp(name, "SDL_GetDisplayName") == 0) return (void *)sdl_stub_android;
    if (strcmp(name, "SDL_GetDisplayContentScale") == 0) return (void *)sdl_stub_onef;
    if (strcmp(name, "SDL_GetSystemTheme") == 0) return (void *)sdl_stub_zero;
    /* Generic fallback so SDLVideo.<clinit> can finish. */
    if (strncmp(name, "SDL_Set", 7) == 0) return (void *)sdl_stub_true;
    if (strncmp(name, "SDL_Show", 8) == 0) return (void *)sdl_stub_true;
    if (strncmp(name, "SDL_Hide", 8) == 0) return (void *)sdl_stub_true;
    if (strncmp(name, "SDL_Raise", 9) == 0) return (void *)sdl_stub_true;
    if (strncmp(name, "SDL_Flash", 9) == 0) return (void *)sdl_stub_true;
    if (strncmp(name, "SDL_Sync", 8) == 0) return (void *)sdl_stub_true;
    if (strncmp(name, "SDL_Destroy", 11) == 0) return (void *)sdl_stub_void;
    return (void *)sdl_stub_true;
}

static void *resolve_openal_soft_event(const char *name) {
    if (name == NULL) return NULL;
    if (strcmp(name, "alcEventIsSupportedSOFT") == 0) {
        return (void *) alcEventIsSupportedSOFT_stub;
    }
    if (strcmp(name, "alcEventControlSOFT") == 0) {
        return (void *) alcEventControlSOFT_stub;
    }
    if (strcmp(name, "alcEventCallbackSOFT") == 0) {
        return (void *) alcEventCallbackSOFT_stub;
    }
    return NULL;
}

/* LWJGL 3.4.1 uses FFM SymbolLookup (dlsym), not DynamicLinkLoader.ndlsym. */
static void *hook_dlsym(void *handle, const char *name) {
    void *r = BYTEHOOK_CALL_PREV(hook_dlsym, typeof(&dlsym), handle, name);
    if (r == NULL) {
        void *stub = resolve_openal_soft_event(name);
        if (stub == NULL) stub = resolve_missing_sdl(name);
        if (stub != NULL) r = stub;
    }
    BYTEHOOK_POP_STACK();
    return r;
}

static void *hook_alcGetProcAddress(void *device, const char *name) {
    typedef void *(*fn_t)(void *, const char *);
    void *r = BYTEHOOK_CALL_PREV(hook_alcGetProcAddress, fn_t, device, name);
    if (r == NULL) {
        void *stub = resolve_openal_soft_event(name);
        if (stub != NULL) r = stub;
    }
    BYTEHOOK_POP_STACK();
    return r;
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */
void installLwjglDlopenHook() {
    __android_log_print(ANDROID_LOG_INFO, "LwjglLinkerHook", "Installing LWJGL dlopen() + OpenAL SOFT stubs");
    bytehook_hook_all(NULL, "dlsym", (void *) hook_dlsym, NULL, NULL);
    bytehook_hook_all(NULL, "alcGetProcAddress", (void *) hook_alcGetProcAddress, NULL, NULL);

    JNIEnv* env = pojav_environ != NULL ? pojav_environ->runtimeJNIEnvPtr_JRE : NULL;
    if (env == NULL) return;
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
