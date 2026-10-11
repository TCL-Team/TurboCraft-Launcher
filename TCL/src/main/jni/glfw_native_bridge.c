#include <android/log.h>
#include <jni.h>
#include "environ/environ.h"
#include "logger/logger.h"

__attribute__((constructor)) static void glfw_native_bridge_loaded(void) {
    __android_log_print(ANDROID_LOG_INFO, "GLFWBridge", "nativeInitializeGLFWNativeBridge is in this libpojavexec");
}

static jboolean ensure_glfw_bridge(JNIEnv *env) {
    if (pojav_environ->vmGlfwClass != NULL) return JNI_TRUE;
    if (env == NULL && pojav_environ->runtimeJavaVMPtr != NULL) {
        (*pojav_environ->runtimeJavaVMPtr)->GetEnv(pojav_environ->runtimeJavaVMPtr, (void **)&env, JNI_VERSION_1_4);
    }
    if (env == NULL) {
        LOG_TO_E("<%s> %s", "Native", "nativeInitializeGLFWNativeBridge: no game JVM environ available!");
        return JNI_FALSE;
    }
    jclass glfwClass = (*env)->FindClass(env, "org/lwjgl/glfw/GLFW");
    if (glfwClass == NULL) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        LOG_TO_E("<%s> %s", "Native", "nativeInitializeGLFWNativeBridge: failed to find GLFW");
        return JNI_FALSE;
    }
    jmethodID setAttrib = (*env)->GetStaticMethodID(env, glfwClass, "glfwSetWindowAttrib", "(JII)V");
    jmethodID sizeChanged = (*env)->GetStaticMethodID(env, glfwClass, "internalWindowSizeChanged", "(J)V");
    jfieldID keyField = (*env)->GetStaticFieldID(env, glfwClass, "keyDownBuffer", "Ljava/nio/ByteBuffer;");
    jfieldID mouseField = (*env)->GetStaticFieldID(env, glfwClass, "mouseDownBuffer", "Ljava/nio/ByteBuffer;");
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, glfwClass);
        LOG_TO_E("<%s> %s", "Native", "nativeInitializeGLFWNativeBridge: member lookup failed");
        return JNI_FALSE;
    }
    jobject keyObj = (*env)->GetStaticObjectField(env, glfwClass, keyField);
    jobject mouseObj = (*env)->GetStaticObjectField(env, glfwClass, mouseField);
    pojav_environ->vmGlfwClass = (*env)->NewGlobalRef(env, glfwClass);
    pojav_environ->method_glftSetWindowAttrib = setAttrib;
    pojav_environ->method_internalWindowSizeChanged = sizeChanged;
    pojav_environ->keyDownBuffer = keyObj ? (*env)->GetDirectBufferAddress(env, keyObj) : NULL;
    pojav_environ->mouseDownBuffer = mouseObj ? (*env)->GetDirectBufferAddress(env, mouseObj) : NULL;
    if (keyObj) (*env)->DeleteLocalRef(env, keyObj);
    if (mouseObj) (*env)->DeleteLocalRef(env, mouseObj);
    (*env)->DeleteLocalRef(env, glfwClass);
    LOG_TO_I("<%s> %s", "Native", "nativeInitializeGLFWNativeBridge: ready");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_org_lwjgl_glfw_GLFW_nativeInitializeGLFWNativeBridge(JNIEnv *env, jclass clazz) {
    (void)clazz;
    ensure_glfw_bridge(env);
}
