package com.movtery.zalithlauncher.game.sdl

import android.app.Activity
import android.view.Surface
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.annotation.MainThread
import org.libsdl.app.SDL
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLSurface
import org.lwjgl.glfw.CallbackBridge
import java.lang.ref.WeakReference

/**
 * Minimal SDL integration state shared by the launcher (Dalvik) and the game JVM hook.
 * Ported from ZalithLauncher2 so Minecraft 26.3 can call SDL_Init on Android.
 */
@Keep
object SdlBridge {
    private var activityRef: WeakReference<Activity>? = null
    private var layoutRef: WeakReference<ViewGroup>? = null
    private var currentSurface: Surface? = null
    private var currentSource: Any? = null
    private var jniReady = false
    private var sdlInitialized = false

    @JvmStatic
    var sdlEnabled: Boolean = false

    @JvmStatic
    fun getSdlEnabled(): Boolean = sdlEnabled

    @JvmStatic
    fun setSdlEnabled(value: Boolean) {
        sdlEnabled = value
    }

    @JvmStatic
    @Synchronized
    fun markSdlInitialized(): Boolean {
        if (sdlInitialized) return false
        sdlInitialized = true
        return true
    }

    @JvmStatic
    fun clearSdlInitialized() {
        sdlInitialized = false
    }

    @JvmStatic
    @Synchronized
    fun setupJNI(): Boolean {
        if (jniReady) return true
        SDL.setupJNI()
        activityRef?.get()?.let { SDL.setContext(it) }
        jniReady = true
        return true
    }

    @JvmStatic
    fun getSdlImeAutoShowEnabled(): Boolean = true

    @JvmStatic
    fun requestComposeFocus() {
        // TCL compose IME focus is optional; no-op is safe
    }

    @JvmStatic
    fun setNativeTextInputActive(active: Boolean): Boolean = true

    @JvmStatic
    fun isSdlRenderActive(): Boolean = sdlEnabled

    @JvmStatic
    fun initializeControllerSubsystems() {
        // optional; Controlify / SDL gamepad path
    }

    @JvmStatic
    @MainThread
    fun prepareSurface(activity: Activity, surface: Surface, layout: ViewGroup?, source: Any? = null) {
        activityRef = WeakReference(activity)
        layoutRef = WeakReference(layout)
        currentSurface = surface
        currentSource = source

        if (SDLActivity.getSDLSurface() == null) {
            SDL.initialize()
            SDL.setContext(activity)
            SDLActivity.externalInitialize(SDLSurface(activity), layout, surface)
        } else {
            SDLSurface.setNativeSurface(surface)
        }
    }

    @JvmStatic
    @MainThread
    fun registerSurface(activity: Activity, surface: Surface, layout: ViewGroup?) {
        activityRef = WeakReference(activity)
        layoutRef = WeakReference(layout)
        currentSurface = surface
    }

    @JvmStatic
    @MainThread
    fun beginSurfaceDestroy(source: Any?, surface: Surface?): Boolean {
        return source != null && currentSource === source && surface != null && currentSurface === surface
    }

    @JvmStatic
    @MainThread
    fun unregisterSurface(surface: Surface?) {
        if (surface != null && currentSurface === surface) {
            currentSurface = null
            currentSource = null
        }
    }

    @JvmStatic
    @MainThread
    @Synchronized
    fun reset() {
        currentSurface = null
        currentSource = null
        activityRef = null
        layoutRef = null
        jniReady = false
        sdlInitialized = false
        sdlEnabled = false
        CallbackBridge.clearSdlBridgeState()
        SDLSurface.clearNativeSurface()
        SDL.initialize()
    }
}
