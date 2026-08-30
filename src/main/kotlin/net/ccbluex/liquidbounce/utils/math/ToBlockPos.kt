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


@file:JvmName("MinecraftVectorExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.math

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.lang.Math.fma
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

inline fun Vec3.toBlockPos(
    xOffset: Double = 0.0,
    yOffset: Double = 0.0,
    zOffset: Double = 0.0,
): BlockPos = BlockPos.containing(x + xOffset, y + yOffset, z + zOffset)

fun Vec3.preferOver(other: Vec3): Vec3 {
    val x = if (this.x == 0.0) other.x else this.x
    val y = if (this.y == 0.0) other.y else this.y
    val z = if (this.z == 0.0) other.z else this.z
    return Vec3(x, y, z)
}

// Mutable Vec3d

fun Vec3.set(x: Double = this.x, y: Double = this.y, z: Double = this.z): Vec3 = apply {
    this.x = x
    this.y = y
    this.z = z
}

fun Vec3.set(other: Vec3): Vec3 = set(other.x, other.y, other.z)

fun Vec3.move(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): Vec3 = apply {
    this.x += x
    this.y += y
    this.z += z
}

fun Vec3.move(other: Vec3): Vec3 = move(other.x, other.y, other.z)

fun Vec3.scaleMut(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0): Vec3 = apply {
    this.x *= x
    this.y *= y
    this.z *= z
}

fun Vec3.scaleMut(scale: Double = 1.0): Vec3 = scaleMut(x = scale, y = scale, z = scale)
