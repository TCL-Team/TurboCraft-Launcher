package com.movtery.zalithlauncher.game.sdl

import android.view.KeyEvent
import org.libsdl.app.SDLActivity

object SdlTextSender {
    @JvmStatic
    fun sendEnter() {
        if (!SdlBridge.sdlEnabled) return
        SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_ENTER)
        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_ENTER)
    }

    @JvmStatic
    fun sendKey(androidKeyCode: Int) {
        if (!SdlBridge.sdlEnabled) return
        if (androidKeyCode == KeyEvent.KEYCODE_UNKNOWN) return
        SDLActivity.onNativeKeyDown(androidKeyCode)
        SDLActivity.onNativeKeyUp(androidKeyCode)
    }
}
