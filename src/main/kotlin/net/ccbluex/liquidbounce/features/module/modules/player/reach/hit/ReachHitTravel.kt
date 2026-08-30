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
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.ReachHitCombatBridge
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.network.MovePacketType
import net.ccbluex.liquidbounce.features.network.sendPacketSilently
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

internal suspend fun ReachHitRuntime.travel(
    from: Vec3,
    to: Vec3,
    rotation: Rotation,
    mode: ReachHitMode,
): Boolean = when (mode) {
    ReachHitMode.PACKET -> travelImmediately(from, to, rotation)
    ReachHitMode.PULSE -> travelWithDelay(from, to, rotation)
    else -> false
}

private fun ReachHitRuntime.travelImmediately(from: Vec3, to: Vec3, rotation: Rotation): Boolean {
    if (setbackDetected) return false
    val steps = buildReachHitTravelPath(
        ReachHitMode.PACKET,
        from,
        to,
        owner.modeConfiguration.packet.stepSize.toDouble(),
    )
    if (steps.isEmpty()) return false
    var previous = from
    for (step in steps) {
        if (setbackDetected) return false
        travelSegment(previous, step, rotation)
        previous = step
    }
    return !setbackDetected
}

private suspend fun ReachHitRuntime.travelWithDelay(from: Vec3, to: Vec3, rotation: Rotation): Boolean {
    val config = owner.modeConfiguration.pulse
    val steps = buildReachHitTravelPath(ReachHitMode.PULSE, from, to, config.stepSize.toDouble())
    return travelPath(steps, rotation, delayTicks = config.delay)
}

internal suspend fun ReachHitRuntime.travelPath(
    path: List<Vec3>,
    rotation: Rotation,
    delayTicks: Int = 0,
    onGround: Boolean = player.onGround(),
): Boolean {
    for ((index, step) in path.withIndex()) {
        if (setbackDetected) return false
        sendPosition(step, rotation, onGround)
        if (delayTicks > 0 && index < path.lastIndex) waitTicks(delayTicks)
    }
    return !setbackDetected
}

private fun ReachHitRuntime.travelSegment(from: Vec3, to: Vec3, rotation: Rotation) {
    val packetType = MovePacketType.FULL
    val repeats = intermediatePacketCount(from, to)
    repeat(repeats) {
        sendMove(packetType, from, rotation, player.onGround())
    }
    sendMove(packetType, to, rotation, player.onGround())
    desyncPlayerPosition = to
}

private fun intermediatePacketCount(from: Vec3, to: Vec3): Int {
    val distance = abs(to.x - from.x) + abs(to.y - from.y) + abs(to.z - from.z)
    return (floor(distance / 10) - 1).toInt().coerceAtLeast(0)
}

private fun ReachHitRuntime.sendPosition(position: Vec3, rotation: Rotation, onGround: Boolean) {
    sendMove(MovePacketType.FULL, position, rotation, onGround)
    desyncPlayerPosition = position
}

private fun sendMove(packetType: MovePacketType, position: Vec3, rotation: Rotation, onGround: Boolean) {
    sendPacketSilently(packetType.generatePacket().apply {
        x = position.x
        y = position.y
        z = position.z
        yRot = rotation.yaw
        xRot = rotation.pitch
        this.onGround = onGround
    })
}

internal fun ReachHitRuntime.sendRotation(rotation: Rotation) {
    sendMove(MovePacketType.FULL, player.position(), rotation, player.onGround())
}

internal fun ReachHitRuntime.attackTarget(
    target: LivingEntity,
    fallbackPosition: Vec3,
    keepSprint: Boolean,
    generation: Long,
): Boolean {
    val valid = isExecutionActive(generation) && target.isAlive && !target.isRemoved &&
        ReachHitCombatBridge.shouldAttack(target)
    if (!valid) return false
    val attackPosition = desyncPlayerPosition ?: fallbackPosition
    if (target.squaredBoxedDistanceTo(attackPosition) > owner.attackRange * owner.attackRange) return false
    ReachHitCombatBridge.attack(target, SwingMode.DO_NOT_HIDE, keepSprint)
    return true
}
