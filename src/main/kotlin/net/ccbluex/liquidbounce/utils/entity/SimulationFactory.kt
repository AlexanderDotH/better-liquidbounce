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

package net.ccbluex.liquidbounce.utils.entity

import net.ccbluex.liquidbounce.utils.client.player as clientPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

internal fun createClientSimulation(input: SimulatedPlayer.SimulatedPlayerInput): SimulatedPlayer =
    createSimulation(clientPlayer, input)

internal fun createOtherPlayerSimulation(
    player: Player,
    input: SimulatedPlayer.SimulatedPlayerInput,
    observedVelocity: Vec3,
): SimulatedPlayer = createSimulation(player, input, observedVelocity)

private fun createSimulation(
    player: Player,
    input: SimulatedPlayer.SimulatedPlayerInput,
    observedVelocity: Vec3? = null,
): SimulatedPlayer = SimulatedPlayer(
    player = player,
    input = input,
    pos = player.position(),
    deltaMovement = observedVelocity ?: player.deltaMovement,
    boundingBox = player.boundingBox,
    yRot = player.yRot,
    xRot = player.xRot,
    isSprinting = player.isSprinting,
    fallDistance = player.fallDistance,
    jumpTriggerTime = player.noJumpDelay,
    jumping = player.jumping,
    fallFlying = player.isFallFlying,
    onGround = player.onGround(),
    horizontalCollision = player.horizontalCollision,
    verticalCollision = player.verticalCollision,
    wasTouchingWater = player.isInWater,
    isSwimming = player.isSwimming,
    wasUnderwater = player.isUnderWater,
    fluidInteraction = player.fluidInteraction.copyForSimulation(),
)

internal fun SimulatedPlayer.copySimulation(): SimulatedPlayer = SimulatedPlayer(
    player,
    input,
    pos,
    deltaMovement,
    boundingBox,
    yRot,
    xRot,
    isSprinting,
    fallDistance,
    jumpTriggerTime,
    jumping,
    fallFlying,
    onGround,
    horizontalCollision,
    verticalCollision,
    wasTouchingWater,
    isSwimming,
    wasUnderwater,
    fluidInteraction.copyForSimulation(),
)
