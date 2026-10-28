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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 透视相机距离（模型像素）：越大透视越弱，接近正交投影 */
private const val CAMERA_DISTANCE = 170f

/** 模型视线中心高度（模型像素） */
private const val MODEL_CENTER_Y = 16f

/** 环境光占比 */
private const val AMBIENT = 0.55f

/** 明暗分级缓存的级数 */
private const val SHADE_LEVELS = 32

/**
 * 部件所属的运动链
 */
private enum class Link {
    /** 固定于髋部（躯干自身） */
    HIP,

    /** 跟随躯干旋转（披风） */
    BODY,

    /** 头部链：绕颈部转动，再随躯干转动 */
    HEAD,

    ARM_R,
    ARM_L,
    LEG_R,
    LEG_L
}

/**
 * 一个待光栅化的盒体部件
 */
private class BoxPart(
    val width: Float,
    val height: Float,
    val depth: Float,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val u: Float,
    val v: Float,
    val pivotX: Float,
    val pivotY: Float,
    val pivotZ: Float,
    val link: Link,
    val inflate: Float,
    val mirror: Boolean,
    val isCape: Boolean
)

/**
 * 一个已投影到屏幕的盒面
 */
private class Face {
    /** 四角屏幕坐标：p00(0,1)、p10(2,3)、p11(4,5)、p01(6,7) */
    val screen = FloatArray(8)

    /** 纹理区域（以纹理像素为单位） */
    var u = 0f
    var v = 0f
    var texWidth = 0f
    var texHeight = 0f

    /** 相机空间深度，用于绘制排序 */
    var depth = 0f

    /** 明暗系数 */
    var shade = 1f

    var outer = false
    var cape = false
    var valid = false
    val path = Path()
}

/**
 * 玩家模型渲染器
 *
 * 逐帧把盒体骨架投影到屏幕并用硬件 Canvas 光栅化：
 * 每个盒面按纹理区域建立仿射映射，绘制前用投影四边形裁剪。
 * 相比逐像素软件光栅化，这里把采样交给 GPU，帧率与分辨率解耦。
 *
 * 非线程安全，只应在渲染线程使用。
 */
internal class PetRenderer {
    private val parts = ArrayList<BoxPart>()
    private var partsSignature = ""

    private val faces = ArrayList<Face>()
    private val sortedFaces = ArrayList<Face>()
    private var faceCount = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }
    private val matrix = Matrix()
    private val matrixValues = FloatArray(9).apply {
        this[6] = 0f
        this[7] = 0f
        this[8] = 1f
    }
    private val shadeFilters = arrayOfNulls<ColorMatrixColorFilter>(SHADE_LEVELS + 1)
    private val colorMatrix = ColorMatrix()

    /** 逐帧复用缓冲区，避免几何计算产生垃圾 */
    private val scratchPoint = FloatArray(3)
    private val scratchNormal = FloatArray(3)
    private val scratchRotated = FloatArray(3)
    private val scratchOffset = FloatArray(3)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowShader: RadialGradient? = null
    private var shadowWidth = 0
    private var shadowHeight = 0

    /** 模型在屏幕上的包围盒，供命中测试使用 */
    var boundsLeft = 0f
        private set
    var boundsTop = 0f
        private set
    var boundsRight = 0f
        private set
    var boundsBottom = 0f
        private set

    /** 上一帧画布尺寸，供命中测试换算 */
    private var lastCanvasWidth = 0
    private var lastCanvasHeight = 0

    /**
     * 绘制一帧
     *
     * @param canvas 目标画布（通常是锁定后的 Surface 画布）
     * @param width 画布宽（像素）
     * @param height 画布高（像素）
     * @param skin 皮肤
     * @param cape 披风，null 表示无披风
     * @param pose 当前姿态
     * @param outerLayer 是否绘制双层皮肤
     * @param lighting 方向光照
     */
    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        skin: PetSkin,
        cape: PetTexture?,
        pose: PetPose,
        outerLayer: Boolean,
        lighting: PetLighting
    ) {
        if (width <= 0 || height <= 0) return
        // SRC 模式整幅覆写为透明，硬件与软件画布都支持
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC)

        val slim = skin.arm == ArmModel.Slim && !skin.legacy
        val signature = "${slim}_${skin.legacy}_${cape != null}_$outerLayer"
        if (signature != partsSignature) {
            buildParts(slim, skin.legacy, cape != null, outerLayer)
            partsSignature = signature
        }

        val scale = min(width / 19f, height / 36f)
        val centerX = width / 2f
        val centerY = height * 0.52f
        lastCanvasWidth = width
        lastCanvasHeight = height

        drawShadow(canvas, centerX, centerY + (MODEL_CENTER_Y * scale), scale)

        faceCount = 0
        collectFaces(skin, cape, pose, scale, centerX, centerY, lighting)
        drawFaces(canvas, skin, cape)
    }

    /**
     * 命中测试（归一化坐标）
     */
    fun hitTest(normalizedX: Float, normalizedY: Float): Boolean {
        if (lastCanvasWidth <= 0 || lastCanvasHeight <= 0) return false
        val x = normalizedX * lastCanvasWidth
        val y = normalizedY * lastCanvasHeight
        return x >= boundsLeft && x <= boundsRight && y >= boundsTop && y <= boundsBottom
    }

    //<editor-fold desc="几何">

    private fun buildParts(slim: Boolean, legacy: Boolean, hasCape: Boolean, outer: Boolean) {
        parts.clear()
        val armWidth = if (slim) 3f else 4f
        // 手臂与躯干之间留出极小的缝隙，避免共面接缝出现闪烁
        val armOffset = 4f + armWidth / 2f - 0.06f
        // 腿部略微内缩，避免与躯干侧面共面
        val legInset = 0.06f
        val legWidth = 4f - legInset
        val legDepth = 4f - legInset

        // 躯干
        parts += BoxPart(
            width = 8f, height = 12f, depth = 4f,
            centerX = 0f, centerY = 18f, centerZ = 0f,
            u = 16f, v = 16f,
            pivotX = 0f, pivotY = 12f, pivotZ = 0f,
            link = Link.HIP, inflate = 0f, mirror = false, isCape = false
        )
        if (outer && !legacy) {
            parts += BoxPart(
                width = 8f, height = 12f, depth = 4f,
                centerX = 0f, centerY = 18f, centerZ = 0f,
                u = 16f, v = 32f,
                pivotX = 0f, pivotY = 12f, pivotZ = 0f,
                link = Link.HIP, inflate = 0.5f, mirror = false, isCape = false
            )
        }

        // 头部
        parts += BoxPart(
            width = 8f, height = 8f, depth = 8f,
            centerX = 0f, centerY = 28f, centerZ = 0f,
            u = 0f, v = 0f,
            pivotX = 0f, pivotY = 24f, pivotZ = 0f,
            link = Link.HEAD, inflate = 0f, mirror = false, isCape = false
        )
        if (outer) {
            parts += BoxPart(
                width = 8f, height = 8f, depth = 8f,
                centerX = 0f, centerY = 28f, centerZ = 0f,
                u = 32f, v = 0f,
                pivotX = 0f, pivotY = 24f, pivotZ = 0f,
                link = Link.HEAD, inflate = 1f, mirror = false, isCape = false
            )
        }

        // 手臂：角色右侧在 -X，左侧在 +X
        for (right in booleanArrayOf(true, false)) {
            val sign = if (right) -1f else 1f
            val link = if (right) Link.ARM_R else Link.ARM_L
            // 旧版皮肤所有肢体共用右侧纹理，左臂需要水平镜像
            val mirrorLeft = !right && legacy
            val (u, outerU) = when {
                legacy -> 40f to 40f
                right -> 40f to 40f
                else -> 32f to 48f
            }
            val v = if (!legacy && !right) 48f else 16f
            val outerV = if (!legacy && !right) 48f else 32f
            parts += BoxPart(
                width = armWidth, height = 12f, depth = 4f,
                centerX = sign * armOffset, centerY = 18f, centerZ = 0f,
                u = u, v = v,
                pivotX = sign * armOffset, pivotY = 24f, pivotZ = 0f,
                link = link, inflate = 0f, mirror = mirrorLeft, isCape = false
            )
            if (outer && !legacy) {
                parts += BoxPart(
                    width = armWidth, height = 12f, depth = 4f,
                    centerX = sign * armOffset, centerY = 18f, centerZ = 0f,
                    u = outerU, v = outerV,
                    pivotX = sign * armOffset, pivotY = 24f, pivotZ = 0f,
                    link = link, inflate = 0.5f, mirror = mirrorLeft, isCape = false
                )
            }
        }

        // 腿
        for (right in booleanArrayOf(true, false)) {
            val sign = if (right) -1f else 1f
            val link = if (right) Link.LEG_R else Link.LEG_L
            val mirrorLeft = !right && legacy
            val (u, outerU) = when {
                legacy -> 0f to 0f
                right -> 0f to 0f
                else -> 16f to 0f
            }
            val v = if (!legacy && !right) 48f else 16f
            val outerV = if (!legacy && !right) 48f else 32f
            parts += BoxPart(
                width = legWidth, height = 12f, depth = legDepth,
                centerX = sign * 2f, centerY = 6f, centerZ = 0f,
                u = u, v = v,
                pivotX = sign * 2f, pivotY = 12f, pivotZ = 0f,
                link = link, inflate = 0f, mirror = mirrorLeft, isCape = false
            )
            if (outer && !legacy) {
                parts += BoxPart(
                    width = legWidth, height = 12f, depth = legDepth,
                    centerX = sign * 2f, centerY = 6f, centerZ = 0f,
                    u = outerU, v = outerV,
                    pivotX = sign * 2f, pivotY = 12f, pivotZ = 0f,
                    link = link, inflate = 0.5f, mirror = mirrorLeft, isCape = false
                )
            }
        }

        // 披风：挂在肩后，绕顶部枢轴向后微倾
        if (hasCape) {
            parts += BoxPart(
                width = 10f, height = 16f, depth = 1f,
                centerX = 0f, centerY = 16f, centerZ = -2.5f,
                u = 0f, v = 0f,
                pivotX = 0f, pivotY = 24f, pivotZ = -2f,
                link = Link.BODY, inflate = 0f, mirror = false, isCape = true
            )
        }
    }

    // 六个面的角点表，与 Minecraft 盒体贴图展开顺序一致：
    // +X（角色左）/ -X（角色右）/ +Y（顶）/ -Y（底）/ +Z（前）/ -Z（后）
    private val faceUAxis = intArrayOf(2, 2, 0, 0, 0, 0)
    private val faceVAxis = intArrayOf(1, 1, 2, 2, 1, 1)
    private val faceWAxis = intArrayOf(0, 0, 1, 1, 2, 2)
    private val faceUDir = floatArrayOf(-1f, 1f, 1f, 1f, 1f, -1f)
    private val faceVDir = floatArrayOf(-1f, -1f, 1f, -1f, -1f, -1f)
    private val normalX = floatArrayOf(1f, -1f, 0f, 0f, 0f, 0f)
    private val normalY = floatArrayOf(0f, 0f, 1f, -1f, 0f, 0f)
    private val normalZ = floatArrayOf(0f, 0f, 0f, 0f, 1f, -1f)

    private fun collectFaces(
        skin: PetSkin,
        cape: PetTexture?,
        pose: PetPose,
        scale: Float,
        centerX: Float,
        centerY: Float,
        lighting: PetLighting
    ) {
        val bodyRot = PetMath.euler(pose.bodyRotX, 0f, 0f)
        val headRot = PetMath.euler(pose.headPitch, pose.headYaw, 0f)
        val armRRot = PetMath.euler(pose.armRX, 0f, pose.armRZ)
        val armLRot = PetMath.euler(pose.armLX, 0f, pose.armLZ)
        val legRRot = PetMath.euler(pose.legRX, 0f, 0f)
        val legLRot = PetMath.euler(pose.legLX, 0f, 0f)
        val yawRot = PetMath.rotationY(pose.yaw)

        val lightX: Float
        val lightY: Float
        val lightZ: Float
        run {
            val azimuth = Math.toRadians(lighting.azimuth.toDouble()).toFloat()
            val elevation = Math.toRadians(lighting.elevation.toDouble()).toFloat()
            val lx = cos(elevation) * sin(azimuth)
            val ly = sin(elevation)
            val lz = cos(elevation) * cos(azimuth)
            val length = sqrt(lx * lx + ly * ly + lz * lz).coerceAtLeast(0.0001f)
            lightX = lx / length
            lightY = ly / length
            lightZ = lz / length
        }

        val point = scratchPoint
        val normal = scratchNormal
        val rotated = scratchRotated
        val offset = scratchOffset

        boundsLeft = Float.MAX_VALUE
        boundsTop = Float.MAX_VALUE
        boundsRight = -Float.MAX_VALUE
        boundsBottom = -Float.MAX_VALUE

        for (part in parts) {
            val inflatedW = part.width + part.inflate
            val inflatedH = part.height + part.inflate
            val inflatedD = part.depth + part.inflate

            for (faceIndex in 0..5) {
                // 纹理区域按未膨胀尺寸展开
                val regionU: Float
                val regionV: Float
                val regionW: Float
                val regionH: Float
                when (faceIndex) {
                    0 -> {
                        regionU = part.u + part.depth + part.width
                        regionV = part.v + part.depth
                        regionW = part.depth
                        regionH = part.height
                    }
                    1 -> {
                        regionU = part.u
                        regionV = part.v + part.depth
                        regionW = part.depth
                        regionH = part.height
                    }
                    2 -> {
                        regionU = part.u + part.depth
                        regionV = part.v
                        regionW = part.width
                        regionH = part.depth
                    }
                    3 -> {
                        regionU = part.u + part.depth + part.width
                        regionV = part.v
                        regionW = part.width
                        regionH = part.depth
                    }
                    4 -> {
                        regionU = part.u + part.depth
                        regionV = part.v + part.depth
                        regionW = part.width
                        regionH = part.height
                    }
                    else -> {
                        regionU = part.u + 2f * part.depth + part.width
                        regionV = part.v + part.depth
                        regionW = part.width
                        regionH = part.height
                    }
                }

                val spanU = when (faceIndex) {
                    0, 1 -> inflatedD
                    2, 3 -> inflatedW
                    else -> inflatedW
                }
                val spanV = when (faceIndex) {
                    0, 1 -> inflatedH
                    2, 3 -> inflatedD
                    else -> inflatedH
                }
                val spanW = when (faceIndex) {
                    0, 1 -> inflatedW
                    2, 3 -> inflatedH
                    else -> inflatedD
                }

                val uDir = faceUDir[faceIndex]
                val vDir = faceVDir[faceIndex]
                val uAxis = faceUAxis[faceIndex]
                val vAxis = faceVAxis[faceIndex]
                val wAxis = faceWAxis[faceIndex]

                // 世界法线（判面与光照共用）
                var nx = normalX[faceIndex]
                var ny = normalY[faceIndex]
                var nz = normalZ[faceIndex]
                if (part.mirror) nx = -nx
                applyLinkRotation(
                    part, nx, ny, nz,
                    bodyRot, headRot, armRRot, armLRot, legRRot, legLRot,
                    rotated
                )
                PetMath.transform(yawRot, rotated[0], rotated[1], rotated[2], normal)

                // 背面剔除：只剔除不透明部件，半透明外层两面都画
                if (part.inflate == 0f && normal[2] <= 0f) continue

                val face = obtainFace()
                var depthSum = 0f
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE

                for (iy in 0..1) {
                    for (ix in 0..1) {
                        offset.fill(0f)
                        offset[uAxis] = (ix * spanU - spanU / 2f) * uDir
                        offset[vAxis] = (iy * spanV - spanV / 2f) * vDir
                        offset[wAxis] = spanW / 2f
                        if (part.mirror) offset[0] = -offset[0]

                        applyLinkRotation(
                            part,
                            part.centerX + offset[0],
                            part.centerY + offset[1],
                            part.centerZ + offset[2],
                            bodyRot, headRot, armRRot, armLRot, legRRot, legLRot,
                            point
                        )

                        // 躯干整体起伏与压扁（以脚底为锚）
                        val wy = (point[1] + pose.bodyY) * pose.squash
                        PetMath.transform(yawRot, point[0], wy, point[2], rotated)

                        val perspective = CAMERA_DISTANCE / (CAMERA_DISTANCE - rotated[2])
                        val sx = centerX + rotated[0] * perspective * scale
                        val sy = centerY - (rotated[1] - MODEL_CENTER_Y) * perspective * scale

                        val index = (iy * 2 + ix) * 2
                        face.screen[index] = sx
                        face.screen[index + 1] = sy

                        depthSum += rotated[2]
                        if (sx < minX) minX = sx
                        if (sx > maxX) maxX = sx
                        if (sy < minY) minY = sy
                        if (sy > maxY) maxY = sy
                    }
                }

                // 退化（侧对相机）的面直接丢弃，避免细缝隙
                val pixelArea = abs(
                    (face.screen[2] - face.screen[0]) * (face.screen[7] - face.screen[1]) -
                        (face.screen[6] - face.screen[0]) * (face.screen[3] - face.screen[1])
                )
                if (pixelArea < 0.5f) {
                    face.valid = false
                    continue
                }

                face.depth = depthSum / 4f
                val texScale = if (part.isCape) (cape?.texScale ?: 1f) else skin.texture.texScale
                face.u = regionU * texScale
                face.v = regionV * texScale
                face.texWidth = regionW * texScale
                face.texHeight = regionH * texScale
                face.cape = part.isCape
                face.outer = part.inflate > 0f

                // 方向光照
                var diffuse = normal[0] * lightX + normal[1] * lightY + normal[2] * lightZ
                if (diffuse < 0f) diffuse = 0f
                var shade = AMBIENT + (1f - AMBIENT) * diffuse
                val softness = (lighting.softness / 100f).coerceIn(0f, 1f)
                shade = 1f + (shade - 1f) * (1f - softness)
                shade *= (lighting.brightness / 100f).coerceIn(0.1f, 2f)
                face.shade = shade.coerceIn(0.08f, 1f)

                // 角点顺序 p00 → p10 → p11 → p01，保证四边形不自交
                face.path.reset()
                face.path.moveTo(face.screen[0], face.screen[1])
                face.path.lineTo(face.screen[2], face.screen[3])
                face.path.lineTo(face.screen[4], face.screen[5])
                face.path.lineTo(face.screen[6], face.screen[7])
                face.path.close()
                face.valid = true

                if (minX < boundsLeft) boundsLeft = minX
                if (maxX > boundsRight) boundsRight = maxX
                if (minY < boundsTop) boundsTop = minY
                if (maxY > boundsBottom) boundsBottom = maxY
            }
        }
    }

    /**
     * 按部件所属运动链，把点或法线绕枢轴施加旋转
     *
     * 参数为值传递，结果写入 [out]，因此可以安全地原地复用缓冲区。
     */
    private fun applyLinkRotation(
        part: BoxPart,
        x: Float, y: Float, z: Float,
        bodyRot: FloatArray,
        headRot: FloatArray,
        armRRot: FloatArray,
        armLRot: FloatArray,
        legRRot: FloatArray,
        legLRot: FloatArray,
        out: FloatArray
    ) {
        when (part.link) {
            Link.HIP -> {
                out[0] = x
                out[1] = y
                out[2] = z
            }
            Link.BODY -> {
                PetMath.rotateAbout(bodyRot, 0f, 12f, 0f, x, y, z, out)
            }
            Link.HEAD -> {
                PetMath.rotateAbout(headRot, part.pivotX, part.pivotY, part.pivotZ, x, y, z, out)
                PetMath.rotateAbout(bodyRot, 0f, 12f, 0f, out[0], out[1], out[2], out)
            }
            Link.ARM_R, Link.ARM_L -> {
                val rot = if (part.link == Link.ARM_R) armRRot else armLRot
                PetMath.rotateAbout(rot, part.pivotX, part.pivotY, part.pivotZ, x, y, z, out)
                PetMath.rotateAbout(bodyRot, 0f, 12f, 0f, out[0], out[1], out[2], out)
            }
            Link.LEG_R, Link.LEG_L -> {
                val rot = if (part.link == Link.LEG_R) legRRot else legLRot
                PetMath.rotateAbout(rot, part.pivotX, part.pivotY, part.pivotZ, x, y, z, out)
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="绘制">

    private fun obtainFace(): Face {
        if (faceCount < faces.size) {
            val face = faces[faceCount]
            faceCount++
            return face
        }
        val face = Face()
        faces.add(face)
        faceCount++
        return face
    }

    private fun drawFaces(canvas: Canvas, skin: PetSkin, cape: PetTexture?) {
        // 先画不透明部件，再按深度从远到近画半透明外层
        sortedFaces.clear()
        for (i in 0 until faceCount) {
            val face = faces[i]
            if (face.valid) sortedFaces.add(face)
        }
        sortedFaces.sortWith(
            compareBy<Face> { if (it.outer) 1 else 0 }.thenBy { it.depth }
        )

        for (face in sortedFaces) {
            val texture: PetTexture = if (face.cape) cape ?: continue else skin.texture
            val p00x = face.screen[0]
            val p00y = face.screen[1]
            val p10x = face.screen[2]
            val p10y = face.screen[3]
            val p01x = face.screen[6]
            val p01y = face.screen[7]

            val exx = (p10x - p00x) / face.texWidth
            val exy = (p10y - p00y) / face.texWidth
            val eyx = (p01x - p00x) / face.texHeight
            val eyy = (p01y - p00y) / face.texHeight
            val det = exx * eyy - eyx * exy
            if (abs(det) < 1e-6f) continue

            val a = eyy / det
            val b = -eyx / det
            val d = -exy / det
            val e = exx / det
            val c = face.u - (a * p00x + b * p00y)
            val f = face.v - (d * p00x + e * p00y)

            matrixValues[0] = a
            matrixValues[1] = b
            matrixValues[2] = c
            matrixValues[3] = d
            matrixValues[4] = e
            matrixValues[5] = f
            matrix.setValues(matrixValues)

            paint.colorFilter = shadeFilter(face.shade)
            canvas.save()
            // 投影四边形不一定严格是平行四边形，用真实四边形裁剪
            canvas.clipPath(face.path)
            canvas.concat(matrix)
            canvas.drawBitmap(texture.bitmap, 0f, 0f, paint)
            canvas.restore()
        }
    }

    private fun shadeFilter(shade: Float): ColorMatrixColorFilter {
        val level = (shade.coerceIn(0f, 1f) * SHADE_LEVELS).roundToInt()
        shadeFilters[level]?.let { return it }
        val s = level.toFloat() / SHADE_LEVELS
        colorMatrix.setScale(s, s, s, 1f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        shadeFilters[level] = filter
        return filter
    }

    private fun drawShadow(canvas: Canvas, centerX: Float, groundY: Float, scale: Float) {
        val radiusX = 7.4f * scale
        val radiusY = 1.9f * scale
        if (radiusX <= 0.5f || radiusY <= 0.5f) return
        val width = (radiusX * 2f).roundToInt().coerceAtLeast(2)
        val height = (radiusY * 2f).roundToInt().coerceAtLeast(2)
        if (shadowShader == null || shadowWidth != width || shadowHeight != height) {
            shadowShader = RadialGradient(
                width / 2f, height / 2f, width / 2f,
                intArrayOf(Color.argb(72, 0, 0, 0), Color.argb(26, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
            shadowWidth = width
            shadowHeight = height
        }
        shadowPaint.shader = shadowShader
        canvas.save()
        canvas.translate(centerX - width / 2f, groundY - height / 2f)
        canvas.scale(1f, height.toFloat() / width.toFloat(), width / 2f, width / 2f)
        canvas.drawCircle(width / 2f, width / 2f, width / 2f, shadowPaint)
        canvas.restore()
    }

    //</editor-fold>
}

/**
 * 方向光照参数
 *
 * @param azimuth 光源方位角（度），0 为正前方
 * @param elevation 光源仰角（度）
 * @param brightness 亮度百分比，100 为原始色
 * @param softness 柔和度百分比，越高明暗对比越平缓
 */
internal class PetLighting(
    val azimuth: Float = -38f,
    val elevation: Float = 32f,
    val brightness: Float = 104f,
    val softness: Float = 42f
)
