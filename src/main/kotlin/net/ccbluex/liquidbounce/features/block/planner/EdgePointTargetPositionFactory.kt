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
package net.ccbluex.liquidbounce.features.block.planner

import net.ccbluex.liquidbounce.common.debug.DebugGeometrySink
import net.ccbluex.liquidbounce.common.debug.DebuggedBox
import net.ccbluex.liquidbounce.common.debug.DebuggedPoint
import net.ccbluex.liquidbounce.features.block.config.PositionFactoryConfiguration
import net.ccbluex.liquidbounce.features.block.contract.FaceTargetPositionFactory
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.anyHorizontal
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.ccbluex.liquidbounce.utils.math.geometry.AlignedFace
import net.ccbluex.liquidbounce.utils.math.minus
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.math.unaryMinus
import net.ccbluex.liquidbounce.utils.math.vertices
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class EdgePointTargetPositionFactory(
    val config: PositionFactoryConfiguration,
) : FaceTargetPositionFactory() {

    override fun producePositionOnFace(face: AlignedFace, targetPos: BlockPos): Vec3 {
        val trimmedFace = trimFace(face)
        return if (!player.input.keyPresses.anyHorizontal) {
            aimAtNearestPointToRotationLine(targetPos, trimmedFace)
        } else {
            aimAtFurthestPointToPlayerPosition(targetPos, trimmedFace)
                ?: aimAtNearestPointToRotationLine(targetPos, trimmedFace)
        }
    }

    private fun aimAtNearestPointToRotationLine(targetPos: BlockPos, face: AlignedFace) =
        NearestRotationTargetPositionFactory(config).aimAtNearestPointToRotationLine(targetPos, face)

    private fun aimAtFurthestPointToPlayerPosition(targetPos: BlockPos, face: AlignedFace): Vec3? {
        val box = AABB(face.from, face.to)
        val playerPositionRelativeToTarget = player.position() - targetPos
        val edge = box.vertices.maxByOrNull { it.distanceToSqr(playerPositionRelativeToTarget) } ?: return null

        DebugGeometrySink.publish(PositionFactoryDebug, "Face") {
            DebuggedBox(AABB(face.from, face.to).move(targetPos), Color4b.RED.argb)
        }
        DebugGeometrySink.publish(PositionFactoryDebug, "Edge") {
            DebuggedPoint(edge + targetPos, Color4b.BLUE.argb, size = 0.05)
        }
        return edge
    }
}
