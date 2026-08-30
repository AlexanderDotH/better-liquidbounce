/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.block.contract

import net.ccbluex.liquidbounce.utils.math.geometry.AlignedFace
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

abstract class FaceTargetPositionFactory {

    /**
     * Samples a position relative to [targetPos].
     * [face] is relative to the origin.
     */
    abstract fun producePositionOnFace(face: AlignedFace, targetPos: BlockPos): Vec3?

    protected fun trimFace(face: AlignedFace): AlignedFace {
        val offsets = face.dimensions.scale(0.15)
        val rangeX = trimmedRange(face.from.x, face.to.x, face.center.x, offsets.x)
        val rangeY = trimmedRange(face.from.y, face.to.y, face.center.y, offsets.y)
        val rangeZ = trimmedRange(face.from.z, face.to.z, face.center.z, offsets.z)

        return AlignedFace(
            Vec3(
                face.from.x.coerceIn(rangeX),
                face.from.y.coerceIn(rangeY),
                face.from.z.coerceIn(rangeZ),
            ),
            Vec3(
                face.to.x.coerceIn(rangeX),
                face.to.y.coerceIn(rangeY),
                face.to.z.coerceIn(rangeZ),
            ),
        )
    }

    private fun trimmedRange(from: Double, to: Double, center: Double, offset: Double): ClosedRange<Double> {
        val range = from + offset..to - offset
        return if (range.isEmpty()) center..center else range
    }
}
