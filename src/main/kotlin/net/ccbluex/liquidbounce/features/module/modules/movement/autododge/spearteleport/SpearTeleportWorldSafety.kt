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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport

import net.ccbluex.liquidbounce.utils.entity.wouldFallIntoVoid
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Pose

internal fun isSafeSpearTeleportCandidate(
    world: ClientLevel,
    player: LocalPlayer,
    settings: SpearTeleportSettings,
    candidate: SpearTeleportPoint,
): Boolean {
    val destination = candidate.toVec3()
    val dimensions = player.getDimensions(Pose.STANDING)
    val destinationBox = dimensions.makeBoundingBox(destination)
    val requiresLandingSupport = player.onGround()
    val supported = !requiresLandingSupport || world.getBlockCollisions(
        player,
        destinationBox.move(0.0, -SUPPORT_CHECK_DEPTH, 0.0),
    ).anyNotEmpty()
    val overVoid = requiresLandingSupport && player.wouldFallIntoVoid(destination, world.minY.toDouble())
    val landingSafe = isSpearTeleportCandidateSafe(
        destinationCollisionFree = world.noCollision(player, destinationBox),
        supported = supported,
        overVoid = overVoid,
        routeCollisionFree = true,
        loaded = world.hasChunkAt(BlockPos.containing(destination)),
        withinWorldBorder = world.worldBorder.isWithinBounds(destinationBox),
        requiresLandingSupport = requiresLandingSupport,
    )
    if (!landingSafe) return false

    val path = buildSpearTeleportPath(
        player.position(),
        destination,
        settings.stepDistance,
        settings.maxPackets,
    ) ?: return false
    val routeCollisionFree = buildSpearTeleportCollisionSamples(player.position(), destination).all { point ->
        world.getBlockCollisions(player, dimensions.makeBoundingBox(point)).allEmpty()
    }
    if (!isSpearTeleportCandidateSafe(true, true, false, routeCollisionFree)) return false

    // Keep packet endpoints covered explicitly even though the denser route sweep includes them.
    return path.all { point ->
        world.getBlockCollisions(player, dimensions.makeBoundingBox(point)).allEmpty()
    }
}

private const val SUPPORT_CHECK_DEPTH = 0.05
