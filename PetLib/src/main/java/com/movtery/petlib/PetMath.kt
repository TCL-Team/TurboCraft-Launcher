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

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 3×3 旋转矩阵（行主序）及其运算。
 * 模型空间约定：X 向右、Y 向上、Z 指向观察者；原点位于双脚底部，单位为模型像素。
 */
internal object PetMath {
    fun rotationX(angle: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        return floatArrayOf(
            1f, 0f, 0f,
            0f, c, -s,
            0f, s, c
        )
    }

    fun rotationY(angle: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        return floatArrayOf(
            c, 0f, s,
            0f, 1f, 0f,
            -s, 0f, c
        )
    }

    fun rotationZ(angle: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        return floatArrayOf(
            c, -s, 0f,
            s, c, 0f,
            0f, 0f, 1f
        )
    }

    /** 欧拉角（XYZ 序）对应的旋转矩阵：R = Ry · Rx · Rz */
    fun euler(x: Float, y: Float, z: Float): FloatArray =
        multiply(multiply(rotationY(y), rotationX(x)), rotationZ(z))

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (row in 0..2) {
            for (col in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += a[row * 3 + k] * b[k * 3 + col]
                }
                out[row * 3 + col] = sum
            }
        }
        return out
    }

    /** 矩阵作用于向量，结果写入 [out] */
    fun transform(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[1] * y + m[2] * z
        out[1] = m[3] * x + m[4] * y + m[5] * z
        out[2] = m[6] * x + m[7] * y + m[8] * z
    }

    /** 绕枢轴 [pivot] 旋转的增量 */
    fun rotateAbout(
        m: FloatArray,
        pivotX: Float, pivotY: Float, pivotZ: Float,
        x: Float, y: Float, z: Float,
        out: FloatArray
    ) {
        val dx = x - pivotX
        val dy = y - pivotY
        val dz = z - pivotZ
        out[0] = m[0] * dx + m[1] * dy + m[2] * dz + pivotX
        out[1] = m[3] * dx + m[4] * dy + m[5] * dz + pivotY
        out[2] = m[6] * dx + m[7] * dy + m[8] * dz + pivotZ
    }

    /** 帧率无关的指数平滑 */
    fun smooth(current: Float, target: Float, tau: Float, deltaSeconds: Float): Float {
        if (tau <= 0f) return target
        val k = exp(-deltaSeconds / tau)
        return target + (current - target) * k
    }

    /** 归一化到 (-π, π]，供最短路径回正 */
    fun wrapPi(angle: Float): Float {
        val twoPi = (Math.PI * 2).toFloat()
        var a = angle % twoPi
        if (a > Math.PI.toFloat()) a -= twoPi
        if (a < -Math.PI.toFloat()) a += twoPi
        return a
    }
}
