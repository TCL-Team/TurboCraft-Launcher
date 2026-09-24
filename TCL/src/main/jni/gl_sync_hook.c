/*
 * MC 26.2 title audio plays but FPS stays 0 because the render thread
 * blocks in glClientWaitSync / glWaitSync (MobileGlues + Adreno, and
 * Mojang bug MC-308476 passing timeout in ms instead of ns).
 *
 * LWJGL resolves those symbols through eglGetProcAddress inside
 * liblwjgl_opengl.so / libmobileglues.so — JNI wrappers on GL32C are
 * not used. Hook eglGetProcAddress + the GL symbols themselves.
 */
#include <dlfcn.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <android/log.h>
#include <bytehook.h>

#define TAG "GLSyncHook"
#define GL_ALREADY_SIGNALED 0x911C
#define GL_TIMEOUT_EXPIRED  0x911A

typedef void *GLsync;
typedef unsigned int GLenum;
typedef unsigned int GLbitfield;

typedef void *(*eglGetProcAddress_t)(const char *);

static eglGetProcAddress_t orig_eglGetProcAddress = NULL;
static bool hook_installed = false;

static GLenum hooked_glClientWaitSync(GLsync sync, GLbitfield flags, uint64_t timeout) {
    (void) flags;
    (void) timeout;
    return sync != NULL ? GL_ALREADY_SIGNALED : GL_TIMEOUT_EXPIRED;
}

static void hooked_glWaitSync(GLsync sync, GLbitfield flags, uint64_t timeout) {
    (void) sync;
    (void) flags;
    (void) timeout;
}

static void *hooked_eglGetProcAddress(const char *name) {
    if (name != NULL) {
        if (strcmp(name, "glClientWaitSync") == 0) return (void *) hooked_glClientWaitSync;
        if (strcmp(name, "glWaitSync") == 0) return (void *) hooked_glWaitSync;
    }
    if (orig_eglGetProcAddress != NULL) {
        return orig_eglGetProcAddress(name);
    }
    return BYTEHOOK_CALL_PREV(hooked_eglGetProcAddress, eglGetProcAddress_t, name);
}

void installGlSyncHook(void) {
    if (hook_installed) return;

    void *egl = dlopen("libEGL.so", RTLD_NOW | RTLD_GLOBAL);
    if (egl) orig_eglGetProcAddress = (eglGetProcAddress_t) dlsym(egl, "eglGetProcAddress");
    if (orig_eglGetProcAddress == NULL) {
        orig_eglGetProcAddress = (eglGetProcAddress_t) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
    }

    void *bh = dlopen("libbytehook.so", RTLD_NOW);
    if (bh == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "libbytehook.so missing: %s", dlerror());
        return;
    }

    int (*bytehook_init_p)(int, bool) = dlsym(bh, "bytehook_init");
    bytehook_stub_t (*bytehook_hook_all_p)(const char *, const char *, void *,
                                           bytehook_hooked_t, void *) = dlsym(bh, "bytehook_hook_all");
    if (bytehook_init_p == NULL || bytehook_hook_all_p == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "bytehook symbols missing");
        return;
    }

    int st = bytehook_init_p(BYTEHOOK_MODE_AUTOMATIC, false);
    if (st != 0 && st != 2 /* already inited */) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "bytehook_init failed %d", st);
        return;
    }

    bytehook_hook_all_p(NULL, "eglGetProcAddress", hooked_eglGetProcAddress, NULL, NULL);
    bytehook_hook_all_p(NULL, "glClientWaitSync", hooked_glClientWaitSync, NULL, NULL);
    bytehook_hook_all_p(NULL, "glWaitSync", hooked_glWaitSync, NULL, NULL);
    hook_installed = true;
    __android_log_print(ANDROID_LOG_INFO, TAG, "non-blocking GL sync hooks installed");
}
