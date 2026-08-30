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

import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.utils.entity.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.entity.wouldFallIntoVoid
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.math.anyNotEmpty
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun simulateSpearMovement(
    input: DirectionalInput,
    player: LocalPlayer,
    world: ClientLevel,
): SpearMovementSimulation {
    val simulatedInput = SimulatedPlayer.SimulatedPlayerInput.fromClientPlayer(
        directionalInput = input,
        jump = false,
        sprinting = player.isSprinting,
        sneaking = player.isShiftKeyDown,
    )
    val simulatedPlayer = SimulatedPlayer.fromClientPlayer(simulatedInput)
    return collectSpearMovementSimulation(
        tick = simulatedPlayer::tick,
        sample = {
            SpearMovementSample(
                position = simulatedPlayer.pos.horizontalPosition(),
                colliding = simulatedPlayer.horizontalCollision,
                supported = simulatedPlayer.onGround || isSpearMovementSupported(world, player, simulatedPlayer.boundingBox),
                overVoid = player.wouldFallIntoVoid(simulatedPlayer.pos, world.minY.toDouble()),
            )
        },
    )
}

internal fun RemotePlayer.toSpearThreatCandidate(): SpearThreatCandidate {
    val currentPosition = position()
    val previousPosition = Vec3(xOld, yOld, zOld)
    val usingSpear = isUsingItem && useItem.isSpear
    val kineticWeapon = useItem.get(DataComponents.KINETIC_WEAPON).takeIf { usingSpear }
    return SpearThreatCandidate(
        entityId = id,
        name = scoreboardName,
        position = currentPosition,
        eyePosition = eyePosition,
        lookDirection = lookAngle,
        isHoldingSpear = mainHandItem.isSpear || offhandItem.isSpear,
        isUsingSpear = usingSpear,
        spearUseTicks = ticksUsingItem.takeIf { usingSpear } ?: 0,
        spearDelayTicks = kineticWeapon?.delayTicks,
        spearDamageUseDurationTicks = kineticWeapon?.computeDamageUseDuration(),
        isAlive = isAlive,
        isRemoved = isRemoved,
        isBot = ModuleAntiBot.isBot(this),
        hasSignificantPositionJump = currentPosition.distanceToSqr(previousPosition) >= SIGNIFICANT_POSITION_JUMP_SQ,
        visibilityAgeTicks = tickCount.coerceAtLeast(0),
    )
}

internal fun Vec3.horizontalPosition() = HorizontalPosition(x, z)

internal fun isSpearMovementSupported(world: ClientLevel, player: LocalPlayer, boundingBox: AABB): Boolean =
    world.getBlockCollisions(player, boundingBox.move(0.0, -SUPPORT_CHECK_DEPTH, 0.0)).anyNotEmpty()

private const val SIGNIFICANT_POSITION_JUMP_SQ = 4.0
private const val SUPPORT_CHECK_DEPTH = 0.05
