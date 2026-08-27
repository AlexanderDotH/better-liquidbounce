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

import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFallSafetyContext
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFoliaProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPacketPlanResult
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPlayerPacketShape
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPlayerPacketStep
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPosition
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTransportProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTransportRequest
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipVanillaProfile
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSegment
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableMovement
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableRouteStep
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionRoute
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/** Packet shape captured by a session route before live yaw and collision flags are available. */
internal sealed interface InteractablePacketInstruction {
    val onGround: Boolean

    data class Status(
        override val onGround: Boolean,
    ) : InteractablePacketInstruction

    data class Position(
        val position: Vec3,
        val fullPacket: Boolean,
        override val onGround: Boolean,
        val collisionChecked: Boolean = true,
        val requiresStandableEndpoint: Boolean = false,
    ) : InteractablePacketInstruction
}

internal sealed interface InteractableRouteCompileResult {
    data class Ready(
        val route: InteractableSessionRoute<InteractablePacketInstruction>,
    ) : InteractableRouteCompileResult

    data object VClipUnavailable : InteractableRouteCompileResult
}

/** Converts a transport-neutral A* plan into delivery-confirmed packets and per-step exact inverses. */
internal object InteractableRouteCompiler {

    fun compile(
        plan: InteractableRoutePlan,
        stepDistance: Double,
        vClip: InteractableVClipSettings,
        fallSafety: VClipFallSafetyContext,
    ): InteractableRouteCompileResult {
        require(stepDistance.isFinite() && stepDistance > 0.0) { "Step distance must be finite and positive" }
        val profile = vClip.toProfile()
        val steps = ArrayList<InteractableRouteStep<InteractablePacketInstruction>>()
        var confirmedPosition = plan.origin

        for (segment in plan.outboundSegments) {
            when (segment) {
                is InteractableRouteSegment.Path -> {
                    val compiled = compilePath(segment, confirmedPosition, stepDistance)
                    steps += compiled
                    confirmedPosition = compiled.lastOrNull()?.outbound?.confirmedPosition ?: confirmedPosition
                }
                is InteractableRouteSegment.VerticalClip -> {
                    val compiled = compileVerticalClip(segment, profile, fallSafety)
                        ?: return InteractableRouteCompileResult.VClipUnavailable
                    steps += compiled
                    confirmedPosition = segment.to
                }
            }
        }

        if (steps.isEmpty()) {
            steps += InteractableRouteStep(
                outbound = InteractableMovement(
                    InteractablePacketInstruction.Position(
                        position = plan.origin,
                        fullPacket = false,
                        onGround = true,
                    ),
                    plan.origin,
                ),
                inverse = emptyList(),
            )
        }

        return InteractableRouteCompileResult.Ready(InteractableSessionRoute(plan.origin, steps))
    }

    private fun compilePath(
        segment: InteractableRouteSegment.Path,
        initialPosition: Vec3,
        stepDistance: Double,
    ): List<InteractableRouteStep<InteractablePacketInstruction>> {
        val result = ArrayList<InteractableRouteStep<InteractablePacketInstruction>>()
        var previous = initialPosition
        for ((from, to) in segment.points.zipWithNext()) {
            check(from.samePosition(previous)) { "Path compilation must preserve segment continuity" }
            for (destination in interpolate(from, to, stepDistance)) {
                result += positionStep(previous, destination)
                previous = destination
            }
        }
        return result
    }

    private fun compileVerticalClip(
        segment: InteractableRouteSegment.VerticalClip,
        profile: VClipTransportProfile,
        fallSafety: VClipFallSafetyContext,
    ): List<InteractableRouteStep<InteractablePacketInstruction>>? {
        val ready = profile.plan(segment.request(fallSafety)) as? VClipPacketPlanResult.Ready ?: return null
        val result = ArrayList<InteractableRouteStep<InteractablePacketInstruction>>(ready.steps.size)
        var previous = segment.from
        for (packetStep in ready.steps) {
            val confirmed = packetStep.position?.toVec3()?.normalizeEndpoint(segment.to) ?: previous
            val inverse = if (confirmed.samePosition(previous)) {
                emptyList()
            } else {
                compileVClipInverse(profile, confirmed, previous, fallSafety.safeFallDistance) ?: return null
            }
            result += InteractableRouteStep(
                outbound = InteractableMovement(
                    packetStep.toInstruction(requiresStandableEndpoint = confirmed.samePosition(segment.to)),
                    confirmed,
                ),
                inverse = inverse,
            )
            previous = confirmed
        }
        return result.takeIf { previous.samePosition(segment.to) }
    }

    private fun compileVClipInverse(
        profile: VClipTransportProfile,
        from: Vec3,
        to: Vec3,
        safeFallDistance: Double,
    ): List<InteractableMovement<InteractablePacketInstruction>>? {
        val request = VClipTransportRequest(
            origin = from.toVClipPosition(),
            target = to.toVClipPosition(),
            fallSafety = VClipFallSafetyContext(0.0, safeFallDistance),
        )
        val ready = profile.plan(request) as? VClipPacketPlanResult.Ready ?: return null
        var confirmed = from
        return ready.steps.map { packetStep ->
            confirmed = packetStep.position?.toVec3()?.normalizeEndpoint(to) ?: confirmed
            InteractableMovement(
                packetStep.toInstruction(requiresStandableEndpoint = confirmed.samePosition(to)),
                confirmed,
            )
        }.takeIf { confirmed.samePosition(to) }
    }

    private fun positionStep(
        previous: Vec3,
        destination: Vec3,
    ): InteractableRouteStep<InteractablePacketInstruction> = InteractableRouteStep(
        outbound = InteractableMovement<InteractablePacketInstruction>(
            payload = InteractablePacketInstruction.Position(destination, fullPacket = false, onGround = true),
            destination,
        ),
        inverse = listOf(
            InteractableMovement<InteractablePacketInstruction>(
                payload = InteractablePacketInstruction.Position(previous, fullPacket = false, onGround = true),
                previous,
            ),
        ),
    )

    private fun interpolate(from: Vec3, to: Vec3, stepDistance: Double): List<Vec3> {
        val distance = from.distanceTo(to)
        if (distance <= POSITION_EPSILON) return emptyList()
        val count = ceil(distance / stepDistance).toInt().coerceAtLeast(1)
        return (1..count).map { index ->
            if (index == count) to else from.lerp(to, index.toDouble() / count)
        }
    }
}

private fun InteractableVClipSettings.toProfile(): VClipTransportProfile = when (this) {
    is InteractableVClipSettings.Vanilla -> VClipVanillaProfile(paperBypass, fullPacket)
    is InteractableVClipSettings.Folia -> VClipFoliaProfile(movementPackets, fullPacket)
}

private fun InteractableRouteSegment.VerticalClip.request(fallSafety: VClipFallSafetyContext) =
    VClipTransportRequest(from.toVClipPosition(), to.toVClipPosition(), fallSafety)

private fun VClipPlayerPacketStep.toInstruction(
    requiresStandableEndpoint: Boolean,
): InteractablePacketInstruction = when (shape) {
    VClipPlayerPacketShape.STATUS_ONLY -> InteractablePacketInstruction.Status(onGround)
    VClipPlayerPacketShape.POSITION -> InteractablePacketInstruction.Position(
        requireNotNull(position).toVec3(),
        fullPacket = false,
        onGround = onGround,
        collisionChecked = false,
        requiresStandableEndpoint = requiresStandableEndpoint,
    )
    VClipPlayerPacketShape.FULL -> InteractablePacketInstruction.Position(
        requireNotNull(position).toVec3(),
        fullPacket = true,
        onGround = onGround,
        collisionChecked = false,
        requiresStandableEndpoint = requiresStandableEndpoint,
    )
}

private fun Vec3.toVClipPosition() = VClipPosition(x, y, z)

private fun VClipPosition.toVec3() = Vec3(x, y, z)

private fun Vec3.normalizeEndpoint(endpoint: Vec3): Vec3 = if (samePosition(endpoint)) endpoint else this

private fun Vec3.samePosition(other: Vec3): Boolean = distanceToSqr(other) <= POSITION_EPSILON_SQUARED

private const val POSITION_EPSILON = 1.0E-6
private const val POSITION_EPSILON_SQUARED = POSITION_EPSILON * POSITION_EPSILON
