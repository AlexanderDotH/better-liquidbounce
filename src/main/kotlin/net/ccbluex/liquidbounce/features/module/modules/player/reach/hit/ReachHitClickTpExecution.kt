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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.hit

import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModuleClickTp
import net.ccbluex.liquidbounce.features.module.modules.exploit.clicktp.contract.CubeCraftAutomationTransport
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.features.chat.chat
import net.ccbluex.liquidbounce.utils.text.markAsError
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal suspend fun ReachHitRuntime.executeClickTpHit(
    target: LivingEntity,
    origin: Vec3,
    targetPosition: Vec3,
    rotation: Rotation,
    keepSprint: Boolean,
    generation: Long,
    transport: CubeCraftAutomationTransport,
    stayTicks: Int,
): Boolean {
    val destination = calculateReachHitDestination(
        origin,
        targetPosition,
        player.bbWidth.toDouble(),
        target.bbWidth.toDouble(),
    )
    var outcome = ReachHitRoundTripOutcome.NOT_STARTED
    val started = ModuleClickTp.runCubeCraftAutomationSession(
        transport = transport,
        inheritedMovementOwner = REACH_HIT_MOVEMENT_OWNER,
    ) { teleport ->
        outcome = executeRoundTripReachHit(
            origin,
            destination,
            stayTicks,
            teleport,
            shouldRecover = { player.position().distanceToSqr(origin) > REACH_HIT_HOME_DISTANCE_SQUARED },
            synchronizeRotation = {
                if (isExecutionActive(generation)) sendRotation(rotation)
            },
            attack = { attackTarget(target, player.position(), keepSprint, generation) },
            wait = ::waitTicks,
        )
    }
    if (!started) return false
    reportFailedReturn(outcome, origin, transport)
    return outcome.attacked
}

private fun ReachHitRuntime.reportFailedReturn(
    outcome: ReachHitRoundTripOutcome,
    origin: Vec3,
    transport: CubeCraftAutomationTransport,
) {
    if (outcome.returned || player.position().distanceToSqr(origin) <= REACH_HIT_HOME_DISTANCE_SQUARED) return
    val name = if (transport == CubeCraftAutomationTransport.MOTION) "Motion" else "Sentinel"
    chat(markAsError("$name return failed, use ClickTP or reconnect to resync."))
}
