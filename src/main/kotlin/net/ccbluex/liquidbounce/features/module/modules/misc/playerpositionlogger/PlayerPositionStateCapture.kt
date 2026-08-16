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
package net.ccbluex.liquidbounce.features.module.modules.misc.playerpositionlogger

import net.ccbluex.liquidbounce.features.module.modules.render.playermodel.ServerPlayerModelSnapshot
import net.minecraft.world.entity.player.Player

internal fun Player.capturePositionSample(local: Boolean) = PlayerPositionSample(
    identity = PlayerPositionIdentity(
        entityId = id,
        uuid = stringUUID,
        name = scoreboardName,
        local = local,
    ),
    state = capturePositionState(),
)

internal fun Player.capturePositionState() = PlayerPositionState(
    position = LoggedVector.from(position()),
    previousPosition = LoggedVector(xo, yo, zo),
    trackingPosition = LoggedVector.from(trackingPosition()),
    positionCodecBase = LoggedVector.from(positionCodec.base),
    velocity = LoggedVector.from(deltaMovement),
    rotation = LoggedPlayerRotation(yRot, xRot, yHeadRot, yBodyRot),
    onGround = onGround(),
    horizontalCollision = horizontalCollision,
    verticalCollision = verticalCollision,
    fallDistance = fallDistance.toDouble(),
    passenger = isPassenger,
    vehicleEntityId = vehicle?.id,
    pose = pose.name.lowercase(),
)

internal fun ServerPlayerModelSnapshot.capturePositionState() = PlayerServerPositionState(
    previousPosition = previousPosition?.let(LoggedVector::from),
    position = position?.let(LoggedVector::from),
    previousRotation = previousRotation?.let { LoggedRotation(it.yaw, it.pitch) },
    rotation = rotation?.let { LoggedRotation(it.yaw, it.pitch) },
    onGround = onGround,
    horizontalCollision = horizontalCollision,
)
