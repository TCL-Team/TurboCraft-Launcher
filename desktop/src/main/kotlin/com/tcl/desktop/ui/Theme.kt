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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** TCL ka accent blue (Android launcher ke primary ke qareeb) */
val TclBlue = Color(0xFF1B6EF3)
val TclBlueLight = Color(0xFF5B9BFF)
val TclCardDark = Color(0xCC2A2E3A)
val TclCardLight = Color(0xCCFFFFFF)
val TclBackgroundDark = Color(0xFF14161C)
val TclBackgroundLight = Color(0xFFEDEFF5)

private val darkScheme = darkColorScheme(
    primary = TclBlue,
    onPrimary = Color.White,
    secondary = TclBlueLight,
    background = TclBackgroundDark,
    onBackground = Color(0xFFE8EAF0),
    surface = TclCardDark,
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0x33FFFFFF),
    onSurfaceVariant = Color(0xFFB9BFCC),
    outline = Color(0x33FFFFFF),
)

private val lightScheme = lightColorScheme(
    primary = TclBlue,
    onPrimary = Color.White,
    secondary = TclBlue,
    background = TclBackgroundLight,
    onBackground = Color(0xFF14161C),
    surface = Color.White,
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0x11000000),
    onSurfaceVariant = Color(0xFF5A6070),
    outline = Color(0x22000000),
)

@Composable
fun TclTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, content = content)
}

/**
 * Launcher background — Android version ke blurred wallpaper video ki jagah
 * ek soft gradient + do glowing blobs, taaki cards waisa hi "glass" feel dein.
 */
@Composable
fun LauncherBackground(dark: Boolean = true, content: @Composable () -> Unit) {
    val base = if (dark) {
        Brush.linearGradient(
            listOf(Color(0xFF0E1016), Color(0xFF1B2233), Color(0xFF241C2E))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFF3F5FA), Color(0xFFE6ECFA), Color(0xFFF6EEF4))
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(base)) {
        Box(
            modifier = Modifier
                .size(520.dp)
                .padding(0.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            (if (dark) TclBlue else TclBlueLight).copy(alpha = if (dark) 0.28f else 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

/** TCL ka BackgroundCard ka desktop equivalent */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    dark: Boolean = true,
    alpha: Float = if (dark) 0.55f else 0.75f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                (if (dark) Color(0xFF2B3040) else Color.White).copy(alpha = alpha)
            )
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.08f else 0.6f), shape),
        content = content
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, dim: Boolean = true) {
    Text(
        modifier = modifier,
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dim) 0.7f else 1f)
    )
}

/** Chhota round chip (status, version type etc.) */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val background = if (highlight) TclBlue.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
    val color = if (highlight) TclBlueLight else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Sidebar ka ek item */
@Composable
fun NavItem(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val background = if (selected) TclBlue.copy(alpha = 0.22f) else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (selected) TclBlueLight else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun RowSpacer(width: Int = 8) {
    Box(modifier = Modifier.size(width.dp))
}
