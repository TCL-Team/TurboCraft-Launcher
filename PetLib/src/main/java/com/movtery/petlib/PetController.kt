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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.annotation.FloatRange

private const val TAG = "PetLib"

/** 单帧时间步长上限（秒），宿主卡顿后不做大跨度跳变 */
private const val MAX_STEP_SECONDS = 0.1f

/**
 * PetLib 控制器
 *
 * 持有皮肤、披风、动画与光照状态，并在主线程以纹理视图的表面逐帧绘制玩家模型。
 * 除 [close] 外的状态方法都可以从任意线程调用；渲染只发生在主线程，
 * 因此姿态数据无需加锁。释放后调用任何方法均为无操作。
 *
 * @param initialSkin 初始皮肤数据（PNG）
 * @param arm 手臂模型，null 表示按皮肤自动检测
 * @param defaultYaw 默认偏航角（弧度），回正时回到该角度
 * @throws IllegalArgumentException 皮肤数据非法
 */
class PetController(
    initialSkin: ByteArray,
    arm: ArmModel?,
    private val defaultYaw: Float = 0f
) : AutoCloseable {

    private val renderer = PetRenderer()
    private val animator = PetAnimator()

    @Volatile
    private var skin: PetSkin = PetSkin.load(initialSkin, arm)

    @Volatile
    private var cape: PetTexture? = null

    @Volatile
    private var lighting = PetLighting()

    @Volatile
    private var outerLayer = true

    @Volatile
    private var released = false

    /** 离屏帧缓冲：软件光栅到 ARGB 位图，由宿主组件直接绘到界面上 */
    private var frameBitmap: Bitmap? = null
    private var frameCanvas: Canvas? = null

    private var lastFrameNanos = 0L
    private var viewWidth = 0
    private var viewHeight = 0

    init {
        animator.defaultYaw = defaultYaw
        animator.setYawTarget(defaultYaw)
    }

    //<editor-fold desc="皮肤与外观">

    /**
     * 替换皮肤
     * @param skin PNG 皮肤数据
     * @param arm 手臂模型类别，null 表示自动检测类型
     * @param onError 失败时的回调（在调用线程执行）
     */
    fun setSkin(skin: ByteArray, arm: ArmModel?, onError: ((String) -> Unit)? = null) {
        if (released) return
        val parsed = try {
            PetSkin.load(skin, arm)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skin rejected: ${e.message}")
            onError?.invoke(e.message ?: "Invalid skin")
            return
        }
        val previous = this.skin
        this.skin = parsed
        previous.texture.recycle()
    }

    /**
     * 替换披风
     * @param cape 披风 PNG 数据，null 表示移除披风
     * @param onError 失败时的回调（在调用线程执行）
     */
    fun setCape(cape: ByteArray?, onError: ((String) -> Unit)? = null) {
        if (released) return
        val parsed = if (cape == null) {
            null
        } else {
            try {
                PetSkin.loadCape(cape)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Cape rejected: ${e.message}")
                onError?.invoke(e.message ?: "Invalid cape")
                return
            }
        }
        val previous = this.cape
        this.cape = parsed
        previous?.recycle()
    }

    /**
     * 方向光照参数
     * @param azimuth 光源方位角（度）
     * @param elevation 光源仰角（度）
     * @param brightness 亮度百分比，100 为原始色
     * @param softness 柔和度百分比，越高明暗对比越平缓
     */
    fun setLighting(azimuth: Float, elevation: Float, brightness: Float, softness: Float) {
        if (released) return
        lighting = PetLighting(azimuth, elevation, brightness, softness)
    }

    /**
     * 是否绘制双层皮肤
     */
    fun setOuterLayer(enabled: Boolean) {
        outerLayer = enabled
    }

    /**
     * 姿态平滑时间常数（秒），帧率无关的指数平滑
     * @param gaze 注视追随
     * @param yaw 整体偏航
     * @param anim 动画切换的交叉过渡
     * @param recenter 头部回正
     */
    fun setTau(gaze: Float, yaw: Float, anim: Float, recenter: Float) {
        if (released) return
        animator.yawTau = yaw.coerceAtLeast(0f)
        animator.recenterTau = recenter.coerceAtLeast(0f)
        if (anim > 0f) animator.poseTau = anim
        if (gaze > 0f) animator.poseTau = minOf(animator.poseTau, gaze)
    }

    /**
     * 注视夹角限制，超出的注视增量被截断
     * @param x 水平夹角上限（度）
     * @param y 垂直夹角上限（度）
     */
    fun setHeadRange(x: Float, y: Float) {
        if (released) return
        animator.headRangeX = x.coerceIn(0f, 89f)
        animator.headRangeY = y.coerceIn(0f, 89f)
    }

    /**
     * 设置压扁动画深度百分比
     */
    fun setSquashDepth(@FloatRange(from = 0.0, to = 30.0) depth: Float) {
        if (released) return
        animator.squashDepth = depth.coerceIn(0f, 30f)
    }

    /**
     * 超采样系数。硬件光栅化本身与分辨率解耦，这里只调整模型占屏比例。
     */
    fun setSuperSample(@FloatRange(from = 0.5, to = 2.0) value: Float) {
        // 保留接口以兼容调用方；硬件光栅化下无需超采样
    }

    //</editor-fold>

    //<editor-fold desc="姿态与动画">

    /**
     * 注视目标（视图像素坐标），null 表示回正
     */
    fun setGaze(target: Pair<Float, Float>?) {
        if (released) return
        if (target == null) {
            animator.setGaze(null, null)
        } else {
            val width = viewWidth.coerceAtLeast(1)
            val height = viewHeight.coerceAtLeast(1)
            animator.setGaze(target.first / width, target.second / height)
        }
    }

    /** 整体偏航目标角（弧度） */
    fun setModelYaw(yaw: Float) {
        if (released) return
        animator.setYawTarget(yaw)
    }

    /** 在当前偏航目标角上叠加增量（弧度），供拖拽旋转 */
    fun rotateYaw(delta: Float) {
        if (released) return
        animator.rotateYaw(delta)
    }

    /** 回正：从当前姿态以最短路径转回默认偏航角 */
    fun recenterYaw() {
        if (released) return
        animator.recenterYaw()
    }

    /** 查询当前偏航角，结果异步回到调用方的回调 */
    fun getModelYaw(onResult: (Float) -> Unit) {
        onResult(animator.pose.yaw)
    }

    /**
     * 播放循环动画
     * @param animation 动画预设
     * @param speed 播放速度倍率
     * @param headFollow 播放期间头部是否追随注视输入
     */
    fun playAnimation(animation: PetAnimation, speed: Float = 1f, headFollow: Boolean = true) {
        if (released) return
        animator.setAnimation(animation, speed, headFollow)
    }

    /** 停止动画，回到静止姿态 */
    fun stopAnimation() {
        if (released) return
        animator.stopAnimation()
    }

    /** 播放点按压扁动画 */
    fun squashTap(now: Float) {
        if (released) return
        animator.squashTap()
    }

    /** 播放攻击摆臂动画 */
    fun playSwing(now: Float) {
        if (released) return
        animator.swing()
    }

    /**
     * 播放点击反应动画，播放中重复调用时无效果
     */
    fun playInteract() {
        if (released) return
        animator.interact()
    }

    /**
     * 归一化坐标命中测试
     * @param x 横坐标（0..1）
     * @param y 纵坐标（0..1）
     */
    fun hitTest(x: Float, y: Float, onResult: (Boolean) -> Unit) {
        onResult(renderer.hitTest(x, y))
    }

    //</editor-fold>

    //<editor-fold desc="渲染">

    /**
     * 把当前姿态光栅到内部帧缓冲并返回该位图
     *
     * 由宿主组件在自身的绘制阶段调用；渲染结果直接绘到界面画布上，
     * 不依赖 TextureView/Surface 这一层间接传递。
     *
     * @param width 目标宽（像素）
     * @param height 目标高（像素）
     * @param frameTimeNanos 帧时间戳，来自宿主帧时钟
     * @return 已绘制的帧位图，尺寸非法或已释放时为 null
     */
    fun renderFrame(width: Int, height: Int, frameTimeNanos: Long): Bitmap? {
        if (released) return null
        if (width <= 0 || height <= 0) return null
        viewWidth = width
        viewHeight = height

        // 首帧把姿态直接对齐到目标，避免从零姿态漫入
        if (lastFrameNanos == 0L) {
            animator.snap()
        } else {
            val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f)
                .coerceIn(0f, MAX_STEP_SECONDS)
            animator.update(dt)
        }
        lastFrameNanos = frameTimeNanos

        val canvas = obtainFrameBuffer(width, height) ?: return null
        return try {
            renderer.draw(
                canvas = canvas,
                width = width,
                height = height,
                skin = skin,
                cape = cape,
                pose = animator.pose,
                outerLayer = outerLayer,
                lighting = lighting
            )
            frameBitmap
        } catch (e: Exception) {
            Log.w(TAG, "Frame draw failed", e)
            null
        }
    }

    /**
     * 取回上一次缓存的帧缓冲尺寸变化时的旧位图不再使用
     */
    private fun obtainFrameBuffer(width: Int, height: Int): Canvas? {
        val existing = frameBitmap
        if (existing != null && !existing.isRecycled &&
            existing.width == width && existing.height == height
        ) {
            return frameCanvas
        }
        existing?.recycle()
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to allocate frame buffer", e)
            frameBitmap = null
            frameCanvas = null
            return null
        }
        val canvas = Canvas(bitmap)
        frameBitmap = bitmap
        frameCanvas = canvas
        return canvas
    }

    /** 重置帧计时，用于宿主恢复绘制时避免一次大跨度推进 */
    fun resetFrameClock() {
        lastFrameNanos = 0L
    }

    //</editor-fold>

    override fun close() {
        if (released) return
        released = true
        skin.texture.recycle()
        cape?.recycle()
        cape = null
        frameBitmap?.recycle()
        frameBitmap = null
        frameCanvas = null
    }
}
