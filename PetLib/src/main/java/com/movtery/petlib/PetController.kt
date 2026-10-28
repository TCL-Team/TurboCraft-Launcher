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
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import androidx.annotation.FloatRange

private const val TAG = "PetLib"

/** 连续锁画布失败达到该次数后，判定表面已失效并停止帧驱动 */
private const val SURFACE_FAILURE_LIMIT = 10

/** 帧间隔下限，避免高刷屏上过度绘制 */
private const val MIN_FRAME_INTERVAL_NANOS = 1_000_000_000L / 60L * 9L / 10L

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

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var frameCallback: Choreographer.FrameCallback? = null

    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private var lastFrameNanos = 0L
    private var surfaceFailures = 0

    /** 已成功提交的帧数，供宿主判断首帧是否真正出图 */
    private var renderedFrames = 0

    /** 离屏帧缓冲：先软件光栅到 ARGB 位图，再整幅覆盖到表面画布 */
    private var frameBitmap: Bitmap? = null
    private var frameCanvas: Canvas? = null

    /** 覆盖式拷贝，保留帧缓冲自身的透明度 */
    private val blitPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

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
            val width = surfaceWidth.coerceAtLeast(1)
            val height = surfaceHeight.coerceAtLeast(1)
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

    //<editor-fold desc="渲染循环">

    /**
     * 绑定绘制表面，之后开始逐帧渲染
     */
    internal fun attachSurface(surface: Surface, width: Int, height: Int) {
        if (released) return
        mainHandler.post {
            if (released) return@post
            if (width > 0 && height > 0) {
                this.surfaceWidth = width
                this.surfaceHeight = height
            }
            if (this.surface === surface) {
                if (this.surfaceWidth > 0 && this.surfaceHeight > 0) startFrameLoop()
                return@post
            }
            this.surface = surface
            surfaceFailures = 0
            lastFrameNanos = 0L
            // 尺寸未知时先不启动：等 onSurfaceTextureSizeChanged 补上尺寸后自动出帧
            if (this.surfaceWidth > 0 && this.surfaceHeight > 0) startFrameLoop()
        }
    }

    /**
     * 暂停/恢复渲染。宿主界面不可见时应暂停，避免后台持续占用 GPU。
     */
    fun setPaused(paused: Boolean) {
        if (released) return
        mainHandler.post {
            if (paused) {
                stopFrameLoop()
            } else if (surface != null) {
                lastFrameNanos = 0L
                startFrameLoop()
            }
        }
    }

    internal fun updateSurfaceSize(width: Int, height: Int) {
        if (released) return
        mainHandler.post {
            if (width > 0 && height > 0) {
                this.surfaceWidth = width
                this.surfaceHeight = height
                if (surface != null && frameCallback == null) {
                    lastFrameNanos = 0L
                    startFrameLoop()
                }
            }
        }
    }

    /** 是否已经真正提交过至少一帧 */
    internal fun hasRenderedFrame(): Boolean = renderedFrames > 0

    /**
     * 解绑绘制表面并停止渲染；表面由宿主视图持有，这里只借用，不负责释放
     */
    internal fun detachSurface() {
        if (released) return
        mainHandler.post {
            surface = null
            stopFrameLoop()
        }
    }

    private fun startFrameLoop() {
        if (frameCallback != null || released) return
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (released || surface == null) {
                    frameCallback = null
                    return
                }
                if (frameTimeNanos - lastFrameNanos >= MIN_FRAME_INTERVAL_NANOS) {
                    renderOneFrame(frameTimeNanos)
                }
                if (surface != null && !released) {
                    Choreographer.getInstance().postFrameCallback(this)
                } else {
                    frameCallback = null
                }
            }
        }
        frameCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun stopFrameLoop() {
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
        lastFrameNanos = 0L
    }

    private fun renderOneFrame(frameTimeNanos: Long) {
        val target = surface ?: return
        val width = surfaceWidth
        val height = surfaceHeight
        if (width <= 0 || height <= 0) return

        // 首帧把姿态直接对齐到目标，避免从零姿态漫入
        if (lastFrameNanos == 0L) {
            animator.snap()
        } else {
            val dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            animator.update(dt)
        }
        lastFrameNanos = frameTimeNanos

        val frame = obtainFrameBuffer(width, height) ?: return
        // 软件光栅到离屏位图：不依赖表面是否支持硬件画布，绘制结果可预期
        try {
            renderer.draw(
                canvas = frame,
                width = width,
                height = height,
                skin = skin,
                cape = cape,
                pose = animator.pose,
                outerLayer = outerLayer,
                lighting = lighting
            )
        } catch (e: Exception) {
            Log.w(TAG, "Frame draw failed", e)
            return
        }

        val canvas = lockCanvas(target)
        if (canvas == null) {
            onSurfaceFailure(null)
            return
        }
        try {
            // 整幅覆盖：帧缓冲已按 SRC 清成透明，直接拷入即可正确做透明合成
            canvas.drawBitmap(frameBitmap ?: return, 0f, 0f, blitPaint)
        } catch (e: Exception) {
            Log.w(TAG, "Frame blit failed", e)
        } finally {
            try {
                target.unlockCanvasAndPost(canvas)
                surfaceFailures = 0
                renderedFrames++
            } catch (e: Exception) {
                onSurfaceFailure(e)
            }
        }
    }

    /**
     * 取得与表面等大的离屏帧画布，尺寸变化时重建
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

    /**
     * 锁定软件画布。
     *
     * 纹理视图的表面不保证支持硬件画布（部分设备上 lockHardwareCanvas 会抛异常
     * 或提交出空帧），因此统一走软件画布 + 离屏位图拷入的路径。
     */
    private fun lockCanvas(target: Surface): Canvas? {
        return try {
            target.lockCanvas(null)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to lock canvas", e)
            null
        }
    }

    private fun onSurfaceFailure(error: Exception?) {
        surfaceFailures++
        if (error != null) {
            Log.w(TAG, "Surface unavailable (${surfaceFailures}/$SURFACE_FAILURE_LIMIT)", error)
        }
        if (surfaceFailures >= SURFACE_FAILURE_LIMIT) {
            surface = null
            stopFrameLoop()
        }
    }

    //</editor-fold>

    override fun close() {
        if (released) return
        released = true
        stopFrameLoop()
        surface = null
        mainHandler.post {
            skin.texture.recycle()
            cape?.recycle()
            cape = null
            frameBitmap?.recycle()
            frameBitmap = null
            frameCanvas = null
        }
    }
}
