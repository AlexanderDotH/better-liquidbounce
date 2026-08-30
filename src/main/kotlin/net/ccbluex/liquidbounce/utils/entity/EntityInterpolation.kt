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
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.lang.Math.fma

fun Entity.interpolateCurrentPosition(tickDelta: Float): Vec3 {
    if (this.tickCount == 0) {
        return this.position()
    }

    val tickDelta = tickDelta.toDouble()

    return Vec3(
        fma(tickDelta, this.x - this.xOld, this.xOld),
        fma(tickDelta, this.y - this.yOld, this.yOld),
        fma(tickDelta, this.z - this.zOld, this.zOld),
    )
}

fun Entity.interpolateCurrentRotation(tickDelta: Float): Rotation {
    if (this.tickCount == 0) {
        return this.rotation
    }

    return Rotation(
        fma(tickDelta, this.yRot - this.yRotO, this.yRotO),
        fma(tickDelta, this.xRot - this.xRotO, this.xRotO),
    )
}

fun LivingEntity.interpolateBodyYaw(tickDelta: Float): Float =
    Mth.rotLerp(tickDelta, yBodyRotO, yBodyRot)

fun LivingEntity.interpolateHeadYaw(tickDelta: Float): Float =
    Mth.rotLerp(tickDelta, yHeadRotO, yHeadRot)

fun LivingEntity.interpolatePitch(tickDelta: Float): Float =
    Mth.lerp(tickDelta, xRotO, xRot)
