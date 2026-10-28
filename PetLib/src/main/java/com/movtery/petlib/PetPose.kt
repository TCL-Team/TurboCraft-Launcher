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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 待机动画的呼吸周期（秒） */
private const val IDLE_PERIOD = 5.2f

/** 行走动画的步频（弧度/秒） */
private const val WALK_SPEED = 7.5f

/** 点按压扁动画时长（秒） */
private const val SQUASH_SECONDS = 0.28f

/** 摆臂动画时长（秒） */
private const val SWING_SECONDS = 0.3f

/** 点击反应（打招呼）动画时长（秒） */
private const val INTERACT_SECONDS = 1.2f

/** 注视夹角上限（度） */
private const val GAZE_YAW_LIMIT = 62f
private const val GAZE_PITCH_LIMIT = 30f

/**
 * 模型姿态：既作为动画器的输出目标，也作为渲染器的输入
 *
 * 所有角度均为弧度，模型空间右手系（X 向右、Y 向上、Z 朝向观察者）。
 * `armR` / `legR` 表示角色自身的右手与右腿（屏幕左侧）。
 */
internal class PetPose {
    /** 整体偏航角 */
    var yaw = 0f

    /** 头部左右转动（由注视输入驱动） */
    var headYaw = 0f

    /** 头部上下俯仰（由注视输入驱动） */
    var headPitch = 0f

    /** 头部额外偏移（行走起伏） */
    var headY = 0f

    var armRX = 0f
    var armLX = 0f
    var armRZ = 0f
    var armLZ = 0f

    var legRX = 0f
    var legLX = 0f

    /** 身体上下起伏 */
    var bodyY = 0f

    /** 身体前倾 */
    var bodyRotX = 0f

    /** 垂直压扁比例，1 为原始比例 */
    var squash = 1f

    fun copyFrom(other: PetPose) {
        yaw = other.yaw
        headYaw = other.headYaw
        headPitch = other.headPitch
        headY = other.headY
        armRX = other.armRX
        armLX = other.armLX
        armRZ = other.armRZ
        armLZ = other.armLZ
        legRX = other.legRX
        legLX = other.legLX
        bodyY = other.bodyY
        bodyRotX = other.bodyRotX
        squash = other.squash
    }
}

/**
 * 姿态动画器：把动画预设与瞬时特效合成为目标姿态，
 * 并按帧率无关的指数平滑把当前姿态推向目标姿态，避免动画切换时跳变。
 */
internal class PetAnimator {
    /** 当前姿态（渲染使用） */
    val pose = PetPose()

    /** 目标姿态 */
    private val target = PetPose()

    /** 姿态平滑时间常数（秒） */
    var poseTau = 0.10f

    /** 偏航平滑时间常数（秒） */
    var yawTau = 0.15f

    /** 头部回正时间常数（秒） */
    var recenterTau = 0.35f

    /** 动画播放速度倍率 */
    var animationSpeed = 1f

    /** 当前循环动画 */
    var animation: PetAnimation? = PetAnimation.Idle

    /** 压扁动画深度百分比 */
    var squashDepth = 12f

    /** 注视夹角上限（度） */
    var headRangeX = GAZE_YAW_LIMIT
    var headRangeY = GAZE_PITCH_LIMIT

    /** 默认偏航角（回正目标） */
    var defaultYaw = 0f

    /** 偏航目标角 */
    private var yawTarget = 0f

    /** 注视目标（0..1 归一化，null 表示回正） */
    private var gazeX = 0.5f
    private var gazeY = 0.5f
    private var gazeActive = false

    private var animTime = 0f
    private var squashTime = -1f
    private var swingTime = -1f
    private var interactTime = -1f

    /** 动画期间头部是否追随注视输入 */
    private var headFollow = true

    fun setAnimation(animation: PetAnimation?, speed: Float, headFollow: Boolean) {
        this.animation = animation
        this.animationSpeed = speed.coerceIn(0.05f, 4f)
        this.headFollow = headFollow
        // 切换动画时从头播放，避免脚步相位跳变
        animTime = 0f
    }

    fun stopAnimation() {
        animation = null
    }

    fun setYawTarget(yaw: Float) {
        yawTarget = yaw
    }

    fun getYawTarget(): Float = yawTarget

    fun rotateYaw(delta: Float) {
        yawTarget += delta
    }

    fun recenterYaw() {
        val current = pose.yaw
        yawTarget = current + PetMath.wrapPi(defaultYaw - current)
    }

    fun setGaze(x: Float?, y: Float?) {
        if (x == null || y == null) {
            gazeActive = false
        } else {
            gazeActive = true
            gazeX = x.coerceIn(0f, 1f)
            gazeY = y.coerceIn(0f, 1f)
        }
    }

    fun squashTap() {
        squashTime = 0f
    }

    fun swing() {
        swingTime = 0f
    }

    fun interact() {
        // 播放中重复调用时无效果
        if (interactTime < 0f) interactTime = 0f
    }

    /** 立即把当前姿态对齐到目标（用于首帧） */
    fun snap() {
        updateTargets(0f)
        pose.copyFrom(target)
        pose.yaw = yawTarget
        pose.squash = 1f
    }

    /**
     * 推进动画
     * @param deltaSeconds 距上一帧的时间
     */
    fun update(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.1f)
        if (animation != null) {
            animTime += dt * animationSpeed
        }
        squashTime = if (squashTime >= 0f) squashTime + dt else -1f
        swingTime = if (swingTime >= 0f) swingTime + dt else -1f
        interactTime = if (interactTime >= 0f) interactTime + dt else -1f
        if (squashTime > SQUASH_SECONDS) squashTime = -1f
        if (swingTime > SWING_SECONDS) swingTime = -1f
        if (interactTime > INTERACT_SECONDS) interactTime = -1f

        updateTargets(animTime)

        // 偏航走最短路径
        val yawDelta = PetMath.wrapPi(yawTarget - pose.yaw)
        pose.yaw = PetMath.smooth(0f, yawDelta, yawTau, dt) + pose.yaw
        pose.headYaw = PetMath.smooth(pose.headYaw, target.headYaw, poseTau, dt)
        pose.headPitch = PetMath.smooth(pose.headPitch, target.headPitch, poseTau, dt)
        pose.headY = PetMath.smooth(pose.headY, target.headY, poseTau, dt)
        pose.armRX = PetMath.smooth(pose.armRX, target.armRX, poseTau, dt)
        pose.armLX = PetMath.smooth(pose.armLX, target.armLX, poseTau, dt)
        pose.armRZ = PetMath.smooth(pose.armRZ, target.armRZ, poseTau, dt)
        pose.armLZ = PetMath.smooth(pose.armLZ, target.armLZ, poseTau, dt)
        pose.legRX = PetMath.smooth(pose.legRX, target.legRX, poseTau, dt)
        pose.legLX = PetMath.smooth(pose.legLX, target.legLX, poseTau, dt)
        pose.bodyY = PetMath.smooth(pose.bodyY, target.bodyY, poseTau, dt)
        pose.bodyRotX = PetMath.smooth(pose.bodyRotX, target.bodyRotX, poseTau, dt)
        // 压扁是瞬时的脉冲，直接使用目标值
        pose.squash = target.squash
    }

    private fun updateTargets(time: Float) {
        // 注视与特效是叠加在循环动画之上的增量，每帧先清零
        target.headYaw = 0f
        target.headPitch = 0f
        target.squash = 1f

        // 1. 循环动画
        when (animation) {
            PetAnimation.Walking -> applyWalking(time)
            PetAnimation.Idle -> applyIdle(time)
            null -> applyRest()
        }

        // 2. 注视驱动的头部转动
        if (gazeActive && (headFollow || animation == null)) {
            val dx = (gazeX - 0.5f) * 2f
            val dy = (0.5f - gazeY) * 2f
            target.headYaw += dx * Math.toRadians(headRangeX.toDouble()).toFloat()
            target.headPitch += dy * Math.toRadians(headRangeY.toDouble()).toFloat()
        } else if (!gazeActive) {
            // 没有鼠标时头部缓缓回正
            target.headYaw = 0f
            target.headPitch = 0f
        }

        // 3. 摆臂（攻击）特效
        if (swingTime >= 0f) {
            val t = (swingTime / SWING_SECONDS).coerceIn(0f, 1f)
            val wave = sin(t * PI.toFloat())
            target.armRX -= wave * 1.35f
            target.armRZ += wave * 0.22f
        }

        // 4. 点击反应：抬手打招呼
        if (interactTime >= 0f) {
            val t = (interactTime / INTERACT_SECONDS).coerceIn(0f, 1f)
            val envelope = sin(t * PI.toFloat())
            target.armLZ += envelope * 0.55f
            target.armLX -= envelope * 2.25f
            target.armLZ += sin(t * PI.toFloat() * 5f) * envelope * 0.28f
        }

        // 5. 点按压扁
        target.squash = if (squashTime >= 0f) {
            val t = (squashTime / SQUASH_SECONDS).coerceIn(0f, 1f)
            val pulse = sin(t * PI.toFloat())
            1f - pulse * (squashDepth.coerceIn(0f, 30f) / 100f * 2.4f)
        } else {
            1f
        }
    }

    private fun applyRest() {
        target.armRX = 0f
        target.armLX = 0f
        target.armRZ = 0.05f
        target.armLZ = -0.05f
        target.legRX = 0f
        target.legLX = 0f
        target.bodyY = 0f
        target.bodyRotX = 0f
        target.headY = 0f
    }

    /** 待机：极缓慢的呼吸、手臂微摆与点头 */
    private fun applyIdle(time: Float) {
        val phase = time / IDLE_PERIOD * PI.toFloat() * 2f
        val breath = sin(phase)
        target.armRX = 0.045f * sin(phase + 0.6f)
        target.armLX = -0.045f * sin(phase + 0.6f)
        target.armRZ = 0.07f
        target.armLZ = -0.07f
        target.legRX = 0f
        target.legLX = 0f
        target.bodyY = 0.22f * breath
        target.bodyRotX = 0f
        target.headY = 0.06f * breath
    }

    /** 行走：手脚交替摆动，身体随步伐上下起伏 */
    private fun applyWalking(time: Float) {
        val s = time * WALK_SPEED
        val stride = sin(s)
        target.armRX = 0.62f * stride
        target.armLX = -0.62f * stride
        target.armRZ = 0.06f
        target.armLZ = -0.06f
        target.legRX = -0.45f * stride
        target.legLX = 0.45f * stride
        target.bodyY = 0.42f * abs(cos(s))
        target.bodyRotX = 0.03f
        target.headY = 0.1f * abs(cos(s))
    }
}
