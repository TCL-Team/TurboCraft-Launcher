/*
 * MC 26.2+ calls org.lwjgl.opengl.GL32C.glDeleteSync(J)V as a JNI native.
 * The bundled liblwjgl_opengl.so only exports nglDeleteSync, so the JVM throws
 * UnsatisfiedLinkError and the render thread dies during GUI upload / GlFence.
 *
 * These entry points live in libpojavexec (already loaded by GLFW.<clinit>
 * in the same game classloader) and forward to GLES3 / MobileGlues via
 * eglGetProcAddress / dlsym.
 */
#include <dlfcn.h>
#include <jni.h>
#include <stdint.h>
#include "logger/logger.h"

#ifndef EGLAPIENTRY
#define EGLAPIENTRY
#endif

typedef void *GLsync;
typedef unsigned int GLenum;
typedef unsigned int GLbitfield;
typedef int GLint;
typedef int GLsizei;
typedef long long GLint64;
typedef unsigned int GLuint;

typedef void *(*eglGetProcAddress_t)(const char *);

static void *resolve_gl(const char *name) {
    static eglGetProcAddress_t eglGetProcAddressFn = NULL;
    static int resolved_egl = 0;
    if (!resolved_egl) {
        resolved_egl = 1;
        eglGetProcAddressFn = (eglGetProcAddress_t) dlsym(RTLD_DEFAULT, "eglGetProcAddress");
        if (eglGetProcAddressFn == NULL) {
            void *egl = dlopen("libEGL.so", RTLD_NOW | RTLD_GLOBAL);
            if (egl) eglGetProcAddressFn = (eglGetProcAddress_t) dlsym(egl, "eglGetProcAddress");
        }
    }
    void *p = NULL;
    if (eglGetProcAddressFn) p = eglGetProcAddressFn(name);
    if (p == NULL) p = dlsym(RTLD_DEFAULT, name);
    if (p == NULL) {
        void *gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_GLOBAL);
        if (gles) p = dlsym(gles, name);
    }
    if (p == NULL) {
        LOG_TO_E("<%s> missing GL symbol: %s", "GL32Sync", name);
    }
    return p;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_opengl_GL32C_glDeleteSync(JNIEnv *env, jclass clazz, jlong sync) {
    static void (*fn)(GLsync) = NULL;
    static int once = 0;
    (void) env;
    (void) clazz;
    if (!once) {
        once = 1;
        fn = (void (*)(GLsync)) resolve_gl("glDeleteSync");
    }
    if (fn != NULL && sync != 0) {
        fn((GLsync) (intptr_t) sync);
    }
}

JNIEXPORT void JNICALL
Java_org_lwjgl_opengl_GL32C_nglDeleteSync(JNIEnv *env, jclass clazz, jlong sync) {
    Java_org_lwjgl_opengl_GL32C_glDeleteSync(env, clazz, sync);
}

JNIEXPORT jlong JNICALL
Java_org_lwjgl_opengl_GL32C_glFenceSync(JNIEnv *env, jclass clazz, jint condition, jint flags) {
    static GLsync (*fn)(GLenum, GLbitfield) = NULL;
    static int once = 0;
    (void) env;
    (void) clazz;
    if (!once) {
        once = 1;
        fn = (GLsync (*)(GLenum, GLbitfield)) resolve_gl("glFenceSync");
    }
    if (fn == NULL) return 0;
    return (jlong) (intptr_t) fn((GLenum) condition, (GLbitfield) flags);
}

JNIEXPORT jboolean JNICALL
Java_org_lwjgl_opengl_GL32C_nglIsSync(JNIEnv *env, jclass clazz, jlong sync) {
    static unsigned char (*fn)(GLsync) = NULL;
    static int once = 0;
    (void) env;
    (void) clazz;
    if (!once) {
        once = 1;
        fn = (unsigned char (*)(GLsync)) resolve_gl("glIsSync");
    }
    if (fn == NULL || sync == 0) return JNI_FALSE;
    return fn((GLsync) (intptr_t) sync) ? JNI_TRUE : JNI_FALSE;
}

/* Never block: MobileGlues/Adreno glClientWaitSync can hang forever,
 * which looks like a black launcher loading screen after title music starts. */
JNIEXPORT jint JNICALL
Java_org_lwjgl_opengl_GL32C_nglClientWaitSync(JNIEnv *env, jclass clazz, jlong sync, jint flags, jlong timeout) {
    (void) env;
    (void) clazz;
    (void) flags;
    (void) timeout;
    return sync != 0 ? 0x911C /* GL_ALREADY_SIGNALED */ : 0x911A /* GL_TIMEOUT_EXPIRED */;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_opengl_GL32C_nglWaitSync(JNIEnv *env, jclass clazz, jlong sync, jint flags, jlong timeout) {
    (void) env;
    (void) clazz;
    (void) sync;
    (void) flags;
    (void) timeout;
}

JNIEXPORT void JNICALL
Java_org_lwjgl_opengl_GL32C_nglGetSynciv(JNIEnv *env, jclass clazz, jlong sync, jint pname,
                                         jint bufSize, jlong lengthPtr, jlong valuesPtr) {
    static void (*fn)(GLsync, GLenum, GLsizei, GLsizei *, GLint *) = NULL;
    static int once = 0;
    (void) env;
    (void) clazz;
    if (!once) {
        once = 1;
        fn = (void (*)(GLsync, GLenum, GLsizei, GLsizei *, GLint *)) resolve_gl("glGetSynciv");
    }
    if (fn != NULL && sync != 0) {
        fn((GLsync) (intptr_t) sync, (GLenum) pname, (GLsizei) bufSize,
           lengthPtr ? (GLsizei *) (intptr_t) lengthPtr : NULL,
           valuesPtr ? (GLint *) (intptr_t) valuesPtr : NULL);
    }
}
