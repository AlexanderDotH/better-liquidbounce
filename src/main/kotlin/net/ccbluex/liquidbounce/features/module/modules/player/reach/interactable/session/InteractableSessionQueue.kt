/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session

import net.minecraft.world.phys.Vec3

internal data class InteractableQueuedMovement<P : Any>(
    val movement: InteractableMovement<P>,
)

internal data class InteractablePendingMovement<P : Any>(
    val packetIdentity: Any,
    val queuedMovement: InteractableQueuedMovement<P>,
)

internal data class InteractableRecoveryCheckpoint<P : Any>(
    val position: Vec3,
    val remainingMovements: List<InteractableMovement<P>>,
)
