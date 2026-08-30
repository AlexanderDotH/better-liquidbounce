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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

import net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearteleport.buildSpearTeleportCollisionSamples

import net.ccbluex.liquidbounce.utils.entity.wouldFallIntoVoid
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal object AutoDodgePacketWorldSafety {

    fun isSafe(
        request: AutoDodgePacketUpdateRequest,
        origin: Vec3,
        destination: Vec3,
        requiresSupport: Boolean,
    ): Boolean {
        if (!origin.hasFiniteCoordinates() || !destination.hasFiniteCoordinates() || destination.y != origin.y) {
            return false
        }
        val dimensions = request.player.getDimensions(Pose.STANDING)
        val originBox = dimensions.makeBoundingBox(origin)
        val destinationBox = dimensions.makeBoundingBox(destination)
        val originSafety = inspectBox(request, originBox)
        val destinationSafety = inspectBox(request, destinationBox)
        val routeSafety = buildSpearTeleportCollisionSamples(origin, destination).map {
            inspectBox(request, dimensions.makeBoundingBox(it))
        }
        if (!isAutoDodgePacketRouteSafe(originSafety, destinationSafety, routeSafety)) {
            return false
        }
        if (!requiresSupport) {
            return true
        }
        return isAutoDodgePacketGroundSafe(
            requiresSupport = true,
            originSupported = isSupported(request, originBox),
            destinationSupported = isSupported(request, destinationBox),
            originOverVoid = request.player.wouldFallIntoVoid(origin, request.world.minY.toDouble()),
            destinationOverVoid = request.player.wouldFallIntoVoid(destination, request.world.minY.toDouble()),
        )
    }

    private fun inspectBox(request: AutoDodgePacketUpdateRequest, box: AABB): AutoDodgePacketBoxSafety {
        val min = BlockPos.containing(box.minX, box.minY, box.minZ)
        val max = BlockPos.containing(box.maxX, box.maxY, box.maxZ)
        val loaded = request.world.hasChunksAt(min, max)
        val withinWorldBorder = loaded && request.world.worldBorder.isWithinBounds(box)
        val collisionFree = withinWorldBorder && request.world.noCollision(request.player, box)
        return AutoDodgePacketBoxSafety(loaded, withinWorldBorder, collisionFree)
    }

    private fun isSupported(request: AutoDodgePacketUpdateRequest, box: AABB) =
        request.world.getBlockCollisions(
            request.player,
            box.move(0.0, -SUPPORT_CHECK_DEPTH, 0.0),
        ).anyNotEmpty()

    private fun Vec3.hasFiniteCoordinates() = x.isFinite() && y.isFinite() && z.isFinite()

    private const val SUPPORT_CHECK_DEPTH = 0.05
}

internal data class AutoDodgePacketBoxSafety(
    val loaded: Boolean,
    val withinWorldBorder: Boolean,
    val collisionFree: Boolean,
) {
    val safe: Boolean
        get() = loaded && withinWorldBorder && collisionFree
}

internal fun isAutoDodgePacketRouteSafe(
    origin: AutoDodgePacketBoxSafety,
    destination: AutoDodgePacketBoxSafety,
    sweptRoute: List<AutoDodgePacketBoxSafety>,
): Boolean = origin.safe && destination.safe && sweptRoute.all(AutoDodgePacketBoxSafety::safe)

internal fun isAutoDodgePacketGroundSafe(
    requiresSupport: Boolean,
    originSupported: Boolean,
    destinationSupported: Boolean,
    originOverVoid: Boolean,
    destinationOverVoid: Boolean,
): Boolean = !requiresSupport || originSupported && destinationSupported && !originOverVoid && !destinationOverVoid
