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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanRequest
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import kotlin.math.ceil
import kotlin.math.floor

internal data class Litematica262ScanCube(
    val request: LitematicaScanRequest,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Long,
    val sizeY: Long,
    val sizeZ: Long,
) {
    val volume: Long = sizeX * sizeY * sizeZ

    fun position(index: Long): BlockPos {
        val x = index % sizeX
        val z = index / sizeX % sizeZ
        val y = index / (sizeX * sizeZ)
        return BlockPos(minX + x.toInt(), minY + y.toInt(), minZ + z.toInt())
    }

    fun insideSphere(position: BlockPos): Boolean = position.toDomainPosition()
        .distanceSquaredTo(request.center) <= request.range * request.range

    companion object {
        fun create(request: LitematicaScanRequest, world: ClientLevel): Litematica262ScanCube {
            val minX = ceil(request.center.x - request.range - 0.5).toInt()
            val maxX = floor(request.center.x + request.range - 0.5).toInt()
            val minY = ceil(request.center.y - request.range - 0.5).toInt().coerceAtLeast(world.minY)
            val maxY = floor(request.center.y + request.range - 0.5).toInt().coerceAtMost(world.maxY)
            val minZ = ceil(request.center.z - request.range - 0.5).toInt()
            val maxZ = floor(request.center.z + request.range - 0.5).toInt()
            return Litematica262ScanCube(
                request, minX, minY, minZ,
                (maxX - minX + 1).coerceAtLeast(0).toLong(),
                (maxY - minY + 1).coerceAtLeast(0).toLong(),
                (maxZ - minZ + 1).coerceAtLeast(0).toLong(),
            )
        }
    }
}
