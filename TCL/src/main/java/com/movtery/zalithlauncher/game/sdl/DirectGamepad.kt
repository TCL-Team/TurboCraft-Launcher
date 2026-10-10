package com.movtery.zalithlauncher.game.sdl

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Gamepad SDL path is optional. TurboCraft does not yet have ZL2's
 * sGamepadButtonBuffer / sGamepadAxisBuffer, so this stays a no-op.
 * Minecraft 26.3 window/SDL launch does not need it.
 */
fun handleGamepadKeyEvent(event: KeyEvent): Boolean = false

fun handleGamepadMotionEvent(event: MotionEvent): Boolean = false
