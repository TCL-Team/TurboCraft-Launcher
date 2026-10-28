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
import android.graphics.BitmapFactory

/** 纹理上限，防止解码炸弹 */
private const val MAX_TEXTURE_BYTES = 8 * 1024 * 1024

/**
 * 已解析的模型纹理（皮肤或披风）
 *
 * @param bitmap 位图，纹理坐标以 64 单位为基准，高清皮肤按 [texScale] 换算
 * @param texScale 纹理相对 64 基线的倍数
 */
internal class PetTexture(
    val bitmap: Bitmap,
    val texScale: Float
) {
    /** 纹理宽高（以 64 基线为单位） */
    val unitWidth: Int
        get() = (bitmap.width / texScale).toInt()

    val unitHeight: Int
        get() = (bitmap.height / texScale).toInt()

    fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

/**
 * 皮肤纹理：带旧版 64×32 兼容与臂型自动检测
 *
 * @param legacy 是否旧版 64×32 布局（该布局没有手臂/腿的外层与左肢纹理）
 */
internal class PetSkin(
    val texture: PetTexture,
    val legacy: Boolean,
    val arm: ArmModel
) {
    companion object {
        private val PNG_HEADER = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

        /**
         * 解析并处理皮肤
         * @param bytes PNG 皮肤数据
         * @param arm 显式指定的臂型，null 时按纹理自动检测
         * @throws IllegalArgumentException 数据非法
         */
        fun load(bytes: ByteArray, arm: ArmModel?): PetSkin {
            if (bytes.size > MAX_TEXTURE_BYTES) {
                throw IllegalArgumentException("Skin file must not exceed 8 MB.")
            }
            if (bytes.size < 24 || !bytes.copyOfRange(0, 8).contentEquals(PNG_HEADER)) {
                throw IllegalArgumentException("Please select a PNG skin.")
            }
            // 在解压任意输入前先校验尺寸声明
            val declaredWidth = readInt(bytes, 16)
            val declaredHeight = readInt(bytes, 20)
            if (declaredWidth < 64 || declaredWidth > 1024 || declaredWidth % 64 != 0 ||
                (declaredHeight != declaredWidth && declaredHeight != declaredWidth / 2)
            ) {
                throw IllegalArgumentException(
                    "Skin must be a 64×64, 64×32, or proportional HD PNG, up to 1024×1024."
                )
            }

            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalArgumentException("Skin PNG could not be decoded.")
            val mutable = if (decoded.isMutable) {
                decoded
            } else {
                decoded.copy(Bitmap.Config.ARGB_8888, true) ?: run {
                    decoded.recycle()
                    throw IllegalArgumentException("Skin PNG could not be decoded.")
                }
            }
            if (mutable !== decoded) decoded.recycle()

            val width = mutable.width
            val height = mutable.height
            val legacy = height * 2 == width
            val k = width / 64

            // 旧版皮肤：面部区域若整块不透明，则清空为透明，避免“第二层脸”遮盖
            if (legacy) {
                val pixels = IntArray(width * height)
                mutable.getPixels(pixels, 0, width, 0, 0, width, height)
                var transparent = false
                for (y in 0 until 16 * k) {
                    for (x in 32 * k until 64 * k) {
                        if ((pixels[y * width + x] ushr 24) < 128) {
                            transparent = true
                        }
                    }
                }
                if (!transparent) {
                    for (y in 0 until 16 * k) {
                        for (x in 32 * k until 64 * k) {
                            pixels[y * width + x] = 0
                        }
                    }
                }
                mutable.setPixels(pixels, 0, width, 0, 0, width, height)
            }

            val skinArm = arm ?: if (legacy) ArmModel.Classic else detectArm(mutable)

            return PetSkin(
                texture = PetTexture(mutable, k.toFloat()),
                legacy = legacy,
                arm = skinArm
            )
        }

        /**
         * 披风纹理：64×32 或其等比例高清
         */
        fun loadCape(bytes: ByteArray): PetTexture {
            if (bytes.size > MAX_TEXTURE_BYTES) {
                throw IllegalArgumentException("Cape file must not exceed 8 MB.")
            }
            if (bytes.size < 24 || !bytes.copyOfRange(0, 8).contentEquals(PNG_HEADER)) {
                throw IllegalArgumentException("Please select a PNG cape.")
            }
            val declaredWidth = readInt(bytes, 16)
            val declaredHeight = readInt(bytes, 20)
            if (declaredWidth < 64 || declaredWidth > 1024 || declaredWidth % 64 != 0 ||
                declaredHeight * 2 != declaredWidth
            ) {
                throw IllegalArgumentException(
                    "Cape must be a 64×32 or proportional HD PNG, up to 1024×512."
                )
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalArgumentException("Cape PNG could not be decoded.")
            return PetTexture(decoded, (decoded.width / 64).toFloat())
        }

        private fun readInt(bytes: ByteArray, offset: Int): Int {
            return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
        }

        /**
         * 纤细臂型自动检测：经典布局下这四处条带必然不透明
         */
        private fun detectArm(image: Bitmap): ArmModel {
            val k = image.width / 64
            val regions = arrayOf(
                intArrayOf(50, 16, 2, 4),
                intArrayOf(54, 20, 2, 12),
                intArrayOf(42, 48, 2, 4),
                intArrayOf(46, 52, 2, 12)
            )
            var allBlack = true
            var allWhite = true
            for (region in regions) {
                for (y in region[1] * k until (region[1] + region[3]) * k) {
                    for (x in region[0] * k until (region[0] + region[2]) * k) {
                        val pixel = image.getPixel(x, y)
                        if (pixel ushr 24 != 255) return ArmModel.Slim
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val black = r == 0 && g == 0 && b == 0
                        val white = r == 255 && g == 255 && b == 255
                        allBlack = allBlack && black
                        allWhite = allWhite && white
                    }
                }
            }
            return if (allBlack || allWhite) ArmModel.Slim else ArmModel.Classic
        }
    }
}
