/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package net.ccbluex.liquidbounce.features.module.modules.render.hats.modes

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.ccbluex.liquidbounce.render.addVertex
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.setColor
import net.ccbluex.liquidbounce.utils.math.fastCos
import net.ccbluex.liquidbounce.utils.math.fastSin
import kotlin.math.abs

internal fun VertexConsumer.drawOrbRhombus(
    matrix: PoseStack.Pose,
    x: Float,
    y: Float,
    z: Float,
    rotation: Float,
    size: Float,
    color: Color4b,
) {
    drawOrbRhombusHalf(matrix, x, y, z, rotation, size, color)
    drawOrbRhombusHalf(matrix, x, y, z, rotation, -size, color)
}

private fun VertexConsumer.drawOrbRhombusHalf(
    matrix: PoseStack.Pose,
    x: Float,
    y: Float,
    z: Float,
    rotation: Float,
    signedSize: Float,
    color: Color4b,
) {
    val size = abs(signedSize)
    val sinAngle = rotation.fastSin() * size
    val cosAngle = rotation.fastCos() * size
    val tip = y + signedSize
    val ax = x + sinAngle
    val az = z + cosAngle
    val bx = x + cosAngle
    val bz = z - sinAngle
    val cx = x - sinAngle
    val cz = z - cosAngle
    val dx = x - cosAngle
    val dz = z + sinAngle
    addVertex(matrix, x, tip, z).setColor(color)
    addVertex(matrix, dx, y, dz).setColor(color)
    addVertex(matrix, ax, y, az).setColor(color)
    addVertex(matrix, x, tip, z).setColor(color)
    addVertex(matrix, ax, y, az).setColor(color)
    addVertex(matrix, bx, y, bz).setColor(color)
    addVertex(matrix, x, tip, z).setColor(color)
    addVertex(matrix, bx, y, bz).setColor(color)
    addVertex(matrix, cx, y, cz).setColor(color)
    addVertex(matrix, x, tip, z).setColor(color)
    addVertex(matrix, cx, y, cz).setColor(color)
    addVertex(matrix, dx, y, dz).setColor(color)
}
