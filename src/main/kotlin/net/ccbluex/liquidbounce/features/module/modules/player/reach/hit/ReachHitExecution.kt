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

import net.ccbluex.liquidbounce.features.module.modules.exploit.clicktp.contract.CubeCraftAutomationTransport
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.math.bottomCenter
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal suspend fun ReachHitRuntime.executeAttack(
    target: LivingEntity,
    origin: Vec3,
    targetPosition: Vec3,
    rotation: Rotation,
    keepSprint: Boolean,
    generation: Long,
    travelMode: ReachHitMode,
): Boolean = when (travelMode) {
    ReachHitMode.PACKET, ReachHitMode.PULSE ->
        executePacketHit(target, origin, targetPosition, rotation, keepSprint, generation, travelMode)
    ReachHitMode.A_STAR ->
        executeAStarHit(target, origin, targetPosition, rotation, keepSprint, generation)
    ReachHitMode.ADAPTIVE ->
        executeAdaptiveHit(target, origin, targetPosition, rotation, keepSprint, generation)
    ReachHitMode.MOTION -> executeClickTpHit(
        target,
        origin,
        targetPosition,
        rotation,
        keepSprint,
        generation,
        CubeCraftAutomationTransport.MOTION,
        stayTicks = 0,
    )
    ReachHitMode.SENTINEL -> executeClickTpHit(
        target,
        origin,
        targetPosition,
        rotation,
        keepSprint,
        generation,
        CubeCraftAutomationTransport.PACKET,
        owner.modeConfiguration.sentinel.stayTicks,
    )
}

private suspend fun ReachHitRuntime.executePacketHit(
    target: LivingEntity,
    origin: Vec3,
    destination: Vec3,
    rotation: Rotation,
    keepSprint: Boolean,
    generation: Long,
    travelMode: ReachHitMode,
): Boolean {
    if (!travel(origin, destination, rotation, travelMode)) return false
    val attacked = attackTarget(target, destination, keepSprint, generation)
    if (!setbackDetected) {
        travel(destination, origin, rotation, travelMode)
    }
    return attacked
}

private suspend fun ReachHitRuntime.executeAStarHit(
    target: LivingEntity,
    origin: Vec3,
    targetPosition: Vec3,
    rotation: Rotation,
    keepSprint: Boolean,
    generation: Long,
): Boolean {
    val destination = calculateReachHitDestination(
        origin,
        targetPosition,
        player.bbWidth.toDouble(),
        target.bbWidth.toDouble(),
    )
    val outward = findPath(
        BlockPos.containing(origin),
        BlockPos.containing(destination),
        owner.modeConfiguration.aStar.maxCost,
    ).map { it.bottomCenter }
    if (outward.isEmpty() || !travelPath(outward, rotation, onGround = false)) return false
    val attacked = attackTarget(target, outward.last(), keepSprint, generation)
    if (!setbackDetected) {
        travelPath(buildReachHitAStarReturnPath(origin, outward), rotation, onGround = false)
    }
    return attacked
}
