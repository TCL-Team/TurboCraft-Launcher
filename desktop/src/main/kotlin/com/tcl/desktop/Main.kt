/*
 * TurboCraft Launcher — desktop (Windows) edition
 * Copyright (C) 2025-2026 TCL Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.tcl.desktop

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tcl.desktop.core.AppPaths
import com.tcl.desktop.core.AppState
import com.tcl.desktop.ui.LauncherRoot
import com.tcl.desktop.ui.SplashScreen
import com.tcl.desktop.ui.TclTheme

fun main() {
    AppPaths.ensure()

    application {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope) }
        val dark = state.settings.theme != "light"

        val windowState = rememberWindowState(
            size = DpSize(1180.dp, 760.dp),
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)
        )

        Window(
            // Game process launcher band hone par bhi chalta rehta hai (default)
            onCloseRequest = { exitApplication() },
            state = windowState,
            title = Brand.APP_NAME,
            icon = painterResource("icon/launcher.png"),
        ) {
            TclTheme(dark = dark) {
                if (state.bootstrapped) {
                    LauncherRoot(state = state, dark = dark, onExit = ::exitApplication)
                } else {
                    SplashScreen(state)
                }
            }
        }
    }
}
