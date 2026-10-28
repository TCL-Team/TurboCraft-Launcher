/*
 * TurboCraft Launcher / PetLib
 * Copyright (C) 2025-2026 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.petlib

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * 点按模型命中时的反馈形式
 */
enum class PetTapReaction {
    /** 无反馈 */
    None,

    /** 压扁脉冲 */
    Squash,

    /** 点击反应动画（抬手打招呼） */
    Interact
}

/**
 * 玩家模型预览组件
 *
 * 直接绘制在组件自身的画布上：模型由控制器光栅到内部帧缓冲，
 * 这里每帧把它贴到界面画布，因此不依赖 TextureView/Surface 的
 * 生命周期与画布锁定，界面一定可见、触摸事件也一定可达。
 *
 * @param tapReaction 点按模型命中时的反馈形式
 * @param dragYaw 是否响应拖拽旋转偏航
 * @param active 宿主界面是否可见，不可见时暂停推进动画
 */
@Composable
fun PetPreview(
    controller: PetController,
    modifier: Modifier = Modifier,
    tapReaction: PetTapReaction = PetTapReaction.Squash,
    dragYaw: Boolean = true,
    active: Boolean = true
) {
    // 帧时钟：推进动画并触发重绘
    var frameTime by remember(controller) { mutableStateOf(0L) }

    LaunchedEffect(controller, active) {
        if (!active) return@LaunchedEffect
        controller.resetFrameClock()
        while (true) {
            withFrameNanos { nanos ->
                frameTime = nanos
            }
        }
    }

    val gestureModifier = Modifier.let {
        if (tapReaction != PetTapReaction.None) {
            it.then(Modifier.pointerInput(controller, tapReaction) {
                detectTapGestures(
                    onPress = { offset ->
                        if (size.width > 0 && size.height > 0) {
                            val nx = offset.x / size.width
                            val ny = offset.y / size.height
                            controller.hitTest(nx, ny) { hit ->
                                // 命中测试之外再兜一层宽松区域：模型占满中部时
                                // 点按必定有反馈，不因边缘像素判定失败而无响应
                                val nearModel = nx in 0.18f..0.82f && ny in 0.02f..0.98f
                                if (hit || nearModel) {
                                    when (tapReaction) {
                                        PetTapReaction.Squash ->
                                            controller.squashTap(System.nanoTime() / 1_000_000_000f)
                                        PetTapReaction.Interact -> controller.playInteract()
                                        PetTapReaction.None -> Unit
                                    }
                                }
                            }
                        }
                    }
                )
            })
        } else {
            it
        }
    }.let {
        if (dragYaw) {
            it.then(Modifier.pointerInput(controller) {
                // 整宽水平拖拽对应一整圈偏航
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (size.width > 0) {
                        controller.rotateYaw(dragAmount.x / size.width * 2f * PI.toFloat())
                    }
                }
            })
        } else {
            it
        }
    }

    Canvas(modifier = modifier.then(gestureModifier)) {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        if (width <= 0 || height <= 0) return@Canvas
        val frame = controller.renderFrame(width, height, frameTime) ?: return@Canvas
        if (frame.isRecycled) return@Canvas
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(frame, 0f, 0f, null)
        }
    }
}
