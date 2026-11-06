/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 启动欢迎页的品牌色（取自启动器图标：青柠绿 + 纯黑）
 */
private val TurboGreen = Color(0xFF8BE51B)
private val TurboGreenDeep = Color(0xFF2F7A0B)
private val TurboGreenDark = Color(0xFF6BB80E)

/**
 * 每次打开启动器显示的欢迎屏。
 *
 * 全部动画都围绕 `alpha` 运行（[Animatable] + `alpha` 修饰符）而不是 composable 的透明度
 * 参数，所以即使系统关闭了「动画时长」或处于省电模式，内容也不会整块消失。
 *
 * @param userName 当前账号名，没有账号时为 null
 * @param buttonText 进入按钮的文案
 * @param onEnter 按钮点击 / 动画结束时调用
 */
@Composable
fun WelcomeScreen(
    userName: String?,
    buttonText: String,
    onEnter: () -> Unit
) {
    //手势或按钮进入后，屏蔽自动跳转，避免重复回调
    var enterFired by remember { mutableStateOf(false) }
    val enterOnce: () -> Unit = remember {
        {
            if (!enterFired) {
                enterFired = true
                onEnter()
            }
        }
    }

    //背景整体入场
    val backgroundAlpha = remember { Animatable(0f) }
    //图标 / 标题 / 副标题 / 按钮 依次出现
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val ringAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffset = remember { Animatable(20f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val buttonAlpha = remember { Animatable(0f) }
    //底部进度条
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { backgroundAlpha.animateTo(1f, tween(600)) }
        delay(80)
        launch { ringAlpha.animateTo(1f, tween(900)) }
        launch {
            logoScale.animateTo(1f, tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
        launch { logoAlpha.animateTo(1f, tween(700)) }
        delay(420)
        launch {
            titleAlpha.animateTo(1f, tween(600))
            titleOffset.animateTo(0f, tween(600))
        }
        delay(180)
        launch { subtitleAlpha.animateTo(1f, tween(600)) }
        delay(160)
        launch { buttonAlpha.animateTo(1f, tween(500)) }
        launch { progress.animateTo(1f, tween(WELCOME_DURATION_MS, easing = LinearEasing)) }
        //进度条走完自动进入主界面
        delay(WELCOME_DURATION_MS.toLong())
        if (!enterFired) {
            enterFired = true
            onEnter()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060D07))
    ) {
        //屏幕短边，横屏/竖屏都能保持同样的视觉比例
        val unit: Dp = minOf(maxWidth, maxHeight)
        val compact = maxHeight < 360.dp

        //背景：品牌绿光晕
        BackgroundGlow(
            modifier = Modifier
                .fillMaxSize()
                .alpha(backgroundAlpha.value),
            unit = unit
        )

        //顶部品牌标识 + 中间内容 + 底部进度条
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 24.dp else 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Logo(
                modifier = Modifier
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value)
                    .size(if (compact) (unit * 0.22f).coerceAtMost(112.dp) else (unit * 0.2f).coerceAtMost(132.dp)),
                ringAlpha = ringAlpha.value
            )

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 20.dp))

            LauncherTitle(
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .graphicsLayer { translationY = titleOffset.value * density },
                fontSize = if (compact) 28.sp else 40.sp
            )

            Text(
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .padding(top = if (compact) 0.dp else 2.dp),
                text = stringResource(R.string.welcome_brand_suffix),
                color = TurboGreen.copy(alpha = 0.9f),
                fontSize = if (compact) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (compact) 6.sp else 8.sp
            )

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 12.dp))

            Text(
                modifier = Modifier
                    .alpha(subtitleAlpha.value)
                    .fillMaxWidth(),
                text = stringResource(
                    if (userName.isNullOrBlank()) R.string.welcome_subtitle_guest
                    else R.string.welcome_subtitle_user,
                    userName ?: ""
                ),
                color = Color.White.copy(alpha = 0.66f),
                fontSize = if (compact) 12.sp else 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(if (compact) 14.dp else 26.dp))

            Button(
                modifier = Modifier
                    .alpha(buttonAlpha.value)
                    .height(if (compact) 38.dp else 46.dp),
                onClick = enterOnce,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TurboGreen,
                    contentColor = Color(0xFF0A1400)
                )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    text = buttonText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 13.sp else 15.sp
                )
            }
        }

        //底部：进度条 + 版本号
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = if (compact) 24.dp else 40.dp,
                    end = if (compact) 24.dp else 40.dp,
                    bottom = if (compact) 10.dp else 18.dp
                )
                .alpha(backgroundAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(listOf(TurboGreenDark, TurboGreen))
                        )
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${BuildKeys.LAUNCHER_NAME} • ${BuildConfig.VERSION_NAME}",
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * 背景：深绿到黑的渐变 + 两团品牌色柔光（径向渐变，任何 Android 版本都能正常显示）。
 */
@Composable
private fun BackgroundGlow(
    modifier: Modifier = Modifier,
    unit: Dp
) {
    val density = LocalDensity.current.density
    val infinite = rememberInfiniteTransition(label = "welcome-glow")
    val breathe by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF0B1A0C), Color(0xFF060D07), Color(0xFF04080B))
            )
        )
    ) {
        //左上角柔光
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = -(unit * 0.42f), y = -(unit * 0.48f))
                .size(unit * 1.35f)
                .scale(breathe)
                .background(
                    Brush.radialGradient(
                        colors = listOf(TurboGreenDeep.copy(alpha = 0.42f), Color.Transparent),
                        radius = unit.value * 0.68f * density
                    ),
                    CircleShape
                )
        )
        //右下角柔光
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = unit * 0.34f, y = unit * 0.38f)
                .size(unit * 1.1f)
                .scale(breathe)
                .background(
                    Brush.radialGradient(
                        colors = listOf(TurboGreen.copy(alpha = 0.18f), Color.Transparent),
                        radius = unit.value * 0.55f * density
                    ),
                    CircleShape
                )
        )

        //极细网格纹理，让纯色背景不显得空
        Canvas(modifier = Modifier.fillMaxSize().alpha(0.045f)) {
            val step = size.minDimension / 12f
            var x = 0f
            while (x < size.width) {
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}

/**
 * 启动器图标 + 外圈呼吸光环
 *
 * @param ringAlpha 光环入场的透明度
 */
@Composable
private fun Logo(
    modifier: Modifier = Modifier,
    ringAlpha: Float
) {
    val infinite = rememberInfiniteTransition(label = "welcome-ring")
    val ringScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        //呼吸光环
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(ringScale)
                .alpha(0.55f * ringAlpha)
                .border(
                    width = 1.5.dp,
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, TurboGreen, Color.Transparent, TurboGreenDark)
                    ),
                    shape = CircleShape
                )
        )
        //图标本身
        Image(
            modifier = Modifier
                .fillMaxSize(0.78f)
                .clip(CircleShape),
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null
        )
    }
}

/**
 * 渐变标题文字
 */
@Composable
private fun LauncherTitle(
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            style = MaterialTheme.typography.displaySmall.copy(
                brush = Brush.horizontalGradient(
                    listOf(Color.White, Color(0xFFE8FFD2), TurboGreen)
                )
            ),
            color = Color.Unspecified
        )
    }
}

/**
 * 欢迎屏停留时长（毫秒）。进度条走完自动进入主界面。
 */
private const val WELCOME_DURATION_MS = 2600
