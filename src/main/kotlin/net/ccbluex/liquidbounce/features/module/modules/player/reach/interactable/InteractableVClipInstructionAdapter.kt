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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFoliaProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPlayerPacketShape
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPlayerPacketStep
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPosition
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTransportProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTransportRequest
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipVanillaProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFallSafetyContext
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractablePacketInstruction
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.InteractableVClipSettings
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSegment
import net.minecraft.world.phys.Vec3

internal fun InteractableVClipSettings.toProfile(): VClipTransportProfile = when (this) {
    is InteractableVClipSettings.Vanilla -> VClipVanillaProfile(paperBypass, fullPacket)
    is InteractableVClipSettings.Folia -> VClipFoliaProfile(movementPackets, fullPacket)
}

internal fun InteractableRouteSegment.VerticalClip.request(fallSafety: VClipFallSafetyContext) =
    VClipTransportRequest(from.toVClipPosition(), to.toVClipPosition(), fallSafety)

internal fun VClipPlayerPacketStep.toInstruction(
    requiresStandableEndpoint: Boolean,
    transportBurstId: Int,
): InteractablePacketInstruction = when (shape) {
    VClipPlayerPacketShape.STATUS_ONLY -> InteractablePacketInstruction.Status(onGround, transportBurstId)
    VClipPlayerPacketShape.POSITION -> InteractablePacketInstruction.Position(
        requireNotNull(position).toVec3(),
        fullPacket = false,
        onGround = onGround,
        collisionChecked = false,
        requiresStandableEndpoint = requiresStandableEndpoint,
        transportBurstId = transportBurstId,
    )
    VClipPlayerPacketShape.FULL -> InteractablePacketInstruction.Position(
        requireNotNull(position).toVec3(),
        fullPacket = true,
        onGround = onGround,
        collisionChecked = false,
        requiresStandableEndpoint = requiresStandableEndpoint,
        transportBurstId = transportBurstId,
    )
}

internal fun Vec3.toVClipPosition() = VClipPosition(x, y, z)

internal fun VClipPosition.toVec3() = Vec3(x, y, z)

internal fun Vec3.normalizeEndpoint(endpoint: Vec3): Vec3 = if (samePosition(endpoint)) endpoint else this

internal fun Vec3.samePosition(other: Vec3): Boolean = distanceToSqr(other) <= POSITION_EPSILON_SQUARED

internal const val POSITION_EPSILON = 1.0E-6
internal const val POSITION_EPSILON_SQUARED = POSITION_EPSILON * POSITION_EPSILON
