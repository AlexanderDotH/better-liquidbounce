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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

@file:JvmName("EntityExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.math.minus
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

// Copied from 1.21.4
val Entity.lastPos: Vec3
    get() = Vec3(xo, yo, zo)

val Entity.rotation: Rotation
    get() = Rotation(this.yRot, this.xRot, true)

val LocalPlayer.lastRotation: Rotation
    get() = Rotation(this.yRotLast, this.xRotLast, true)

val Entity.box: AABB
    get() = boundingBox.inflate(pickRadius.toDouble())

private val cameraPos: Vec3 get() = mc.gameRenderer.mainCamera().position()

fun Position.cameraDistanceSq() = cameraPos.distanceToSqr(x(), y(), z())

fun Position.cameraDistance() = sqrt(cameraDistanceSq())

fun Vec3i.cameraDistanceSq() = cameraPos.distanceToSqr(x.toDouble(), y.toDouble(), z.toDouble())

/**
 * Allows to calculate the distance between the current entity and [entity] from the nearest corner of the bounding box
 */
fun Entity.boxedDistanceTo(entity: Entity): Double {
    return sqrt(squaredBoxedDistanceTo(entity))
}

fun Entity.squaredBoxedDistanceTo(entity: Entity): Double {
    return this.squaredBoxedDistanceTo(entity.eyePosition)
}

fun Entity.squaredBoxedDistanceTo(otherPos: Vec3): Double {
    return box.distanceToSqr(otherPos)
}

fun Entity.squareBoxedDistanceTo(entity: Entity, offsetPos: Vec3): Double {
    return box.move(offsetPos - position()).distanceToSqr(entity.eyePosition)
}
