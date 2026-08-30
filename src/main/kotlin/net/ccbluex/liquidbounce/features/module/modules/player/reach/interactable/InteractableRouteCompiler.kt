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
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipPacketPlanResult
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTransportProfile
import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipTransportRequest
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.*
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSegment
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableMovement
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableRouteStep
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionRoute
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/** Converts a transport-neutral A* plan into delivery-confirmed packets and per-step exact inverses. */
internal object InteractableRouteCompiler {

    fun compile(
        plan: InteractableRoutePlan,
        stepDistance: Double,
        maximumVClipDistance: Double,
        vClip: InteractableVClipSettings,
        fallSafety: VClipFallSafetyContext,
    ): InteractableRouteCompileResult {
        validateDistances(stepDistance, maximumVClipDistance)
        val profile = vClip.toProfile()
        val steps = ArrayList<InteractableRouteStep<InteractablePacketInstruction>>()
        var confirmedPosition = plan.origin
        var nextTransportBurstId = 1

        for (segment in plan.outboundSegments) {
            when (segment) {
                is InteractableRouteSegment.Path -> {
                    val compiled = compilePath(segment, confirmedPosition, stepDistance)
                    steps += compiled
                    confirmedPosition = compiled.lastOrNull()?.outbound?.confirmedPosition ?: confirmedPosition
                }
                is InteractableRouteSegment.VerticalClip -> {
                    if (kotlin.math.abs(segment.to.y - segment.from.y) > maximumVClipDistance) {
                        return InteractableRouteCompileResult.VClipDistanceExceeded
                    }
                    val compiled = compileVerticalClip(
                        segment,
                        profile,
                        fallSafety,
                        nextTransportBurstId++,
                    )
                        ?: return InteractableRouteCompileResult.VClipUnavailable
                    steps += compiled
                    confirmedPosition = segment.to
                }
            }
        }

        addOriginStepWhenEmpty(steps, plan.origin)

        return InteractableRouteCompileResult.Ready(InteractableSessionRoute(plan.origin, steps))
    }

    private fun validateDistances(stepDistance: Double, maximumVClipDistance: Double) {
        require(stepDistance.isFinite() && stepDistance > 0.0) { "Step distance must be finite and positive" }
        require(maximumVClipDistance.isFinite() && maximumVClipDistance > 0.0) {
            "Maximum VClip distance must be finite and positive"
        }
    }

    private fun addOriginStepWhenEmpty(
        steps: MutableList<InteractableRouteStep<InteractablePacketInstruction>>,
        origin: Vec3,
    ) {
        if (steps.isNotEmpty()) return
        steps += InteractableRouteStep(
            outbound = InteractableMovement(
                InteractablePacketInstruction.Position(position = origin, fullPacket = false, onGround = true),
                origin,
            ),
            inverse = emptyList(),
        )
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
        transportBurstId: Int,
    ): List<InteractableRouteStep<InteractablePacketInstruction>>? {
        val ready = profile.plan(segment.request(fallSafety)) as? VClipPacketPlanResult.Ready ?: return null
        val result = ArrayList<InteractableRouteStep<InteractablePacketInstruction>>(ready.steps.size)
        var previous = segment.from
        for (packetStep in ready.steps) {
            val confirmed = packetStep.position?.toVec3()?.normalizeEndpoint(segment.to) ?: previous
            val inverse = if (confirmed.samePosition(previous)) {
                emptyList()
            } else {
                compileVClipInverse(
                    profile,
                    confirmed,
                    previous,
                    fallSafety.safeFallDistance,
                    transportBurstId,
                ) ?: return null
            }
            result += InteractableRouteStep(
                outbound = InteractableMovement(
                    packetStep.toInstruction(
                        requiresStandableEndpoint = confirmed.samePosition(segment.to),
                        transportBurstId = transportBurstId,
                    ),
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
        transportBurstId: Int,
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
                packetStep.toInstruction(
                    requiresStandableEndpoint = confirmed.samePosition(to),
                    transportBurstId = transportBurstId,
                ),
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
