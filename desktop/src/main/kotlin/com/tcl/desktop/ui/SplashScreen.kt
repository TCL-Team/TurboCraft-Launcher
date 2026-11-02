/*
 * TurboCraft Launcher — desktop (Windows) edition
 * Copyright (C) 2025-2026 TCL Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.tcl.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcl.desktop.Brand
import com.tcl.desktop.core.AppState

/** TCL logo (resources/icon/launcher.png) — jpackage ke andar bhi milta hai */
internal object LogoLoader {
    private const val PATH = "icon/launcher.png"

    fun stream() = LogoLoader::class.java.classLoader?.getResourceAsStream(PATH)
        ?: ClassLoader.getSystemResourceAsStream(PATH)

    fun bitmap(): androidx.compose.ui.graphics.ImageBitmap? {
        val input = stream() ?: return null
        return runCatching { loadImageBitmap(input) }.getOrNull()
    }
}

/**
 * Android edition jaisa splash: launcher start hote hi components (Java, versions,
 * loaders) prepare hote hain, phir UI khulti hai.
 */
@Composable
fun SplashScreen(state: AppState) {
    LaunchedEffect(Unit) { state.startBootstrap() }

    val steps = state.bootstrapSteps
    val completed = steps.count { it.done || it.failed }
    val target = if (steps.isEmpty()) 0f else completed.toFloat() / steps.size
    val progress by animateFloatAsState(targetValue = target, animationSpec = tween(400), label = "boot")

    val logo = remember { LogoLoader.bitmap() }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF0A0E12), Color(0xFF101A24), Color(0xFF0C1116))
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (logo != null) {
                Image(
                    bitmap = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(148.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, AccentSoft))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(Brand.INITIALS, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(Brand.APP_NAME, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "v${Brand.VERSION_NAME} · Windows",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(34.dp))
            LinearProgressIndicator(
                progress = { if (steps.isEmpty()) 0.02f else progress },
                modifier = Modifier.width(360.dp).height(6.dp).clip(RoundedCornerShape(50))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier.width(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                steps.forEach { step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        step.failed -> MaterialTheme.colorScheme.error
                                        step.done -> Color(0xFF57D9A3)
                                        else -> Color.White.copy(alpha = 0.25f)
                                    }
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = step.label,
                            color = Color.White.copy(alpha = if (step.done) 0.55f else 0.95f),
                            fontSize = 12.sp,
                            modifier = Modifier.width(170.dp)
                        )
                        Text(
                            text = step.detail,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Text(
            text = "Preparing launcher components…",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}
