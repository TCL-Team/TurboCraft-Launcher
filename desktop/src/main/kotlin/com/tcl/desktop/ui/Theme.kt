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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 "blue" palette — Android edition ke mistwave theme jaisa hi feel.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBFF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B70),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFBDC7DC),
    onSecondary = Color(0xFF273141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E2E8),
    surfaceVariant = Color(0xFF3F484F),
    onSurfaceVariant = Color(0xFFBFC8D1),
    outline = Color(0xFF89929A),
    outlineVariant = Color(0xFF3F484F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF171C21),
    surfaceContainer = Color(0xFF1B2025),
    surfaceContainerHigh = Color(0xFF262B31),
    surfaceContainerHighest = Color(0xFF31363C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00639B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF51606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E4F6),
    onSecondaryContainer = Color(0xFF0D1D2A),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEFF5),
    surfaceContainerHigh = Color(0xFFE5E9EF),
    surfaceContainerHighest = Color(0xFFDFE3E9),
)

@Composable
fun TclTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

/** Accent used for hero gradients / highlights only */
val Accent = Color(0xFF2F81F7)
val AccentSoft = Color(0xFF55A6FF)

/**
 * Launcher background — Android ke blurred wallpaper ki jagah do soft glows,
 * taaki glass cards waisa hi depth dein.
 */
@Composable
fun LauncherBackground(dark: Boolean = true, content: @Composable () -> Unit) {
    val base = if (dark) Color(0xFF0B0F13) else Color(0xFFEEF2F8)
    val glowA = if (dark) Color(0xFF1B4C7A) else Color(0xFFBBD7F5)
    val glowB = if (dark) Color(0xFF2A2350) else Color(0xFFE0D3F2)

    Box(modifier = Modifier.fillMaxSize().background(base)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(glowA.copy(alpha = if (dark) 0.45f else 0.7f), Color.Transparent),
                    radius = 1100f
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(glowB.copy(alpha = if (dark) 0.35f else 0.55f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(1400f, 900f),
                    radius = 1200f
                )
            )
        )
        content()
    }
}

/** Android ke `BackgroundCard` ka desktop version */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape),
        content = content
    )
}

/** Card ka title rail — Android ka `CardTitleLayout` */
@Composable
fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, dim: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dim) 0.65f else 0.92f)
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Section heading outside cards */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    }
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

/** Chhota tag (release / snapshot / fabric …) */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val container = if (highlight) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (highlight) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    CompositionLocalProvider(LocalContentColor provides content) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Primary action button (filled) */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (leading != null) leading()
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary action button (tonal) */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    leading: (@Composable RowScope.() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        if (leading != null) leading()
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Thin divider */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

@Composable
fun Gap(height: Int = 8) {
    Box(Modifier.size(height.dp))
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        fontSize = 12.sp
    )
}
