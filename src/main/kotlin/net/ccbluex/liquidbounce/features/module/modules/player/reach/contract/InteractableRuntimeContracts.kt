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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.contract

import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractablePacketDisposition
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionCause
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.session.InteractableSessionState
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.Vec3

/** Opaque target identity owned by the target boundary rather than the Minecraft runtime. */
internal interface InteractableRuntimeTarget

/** Resolves the concrete interaction while keeping target implementation types out of the runtime. */
internal fun interface InteractableInteractionPort {
    fun resolve(
        target: InteractableRuntimeTarget,
        eyePosition: Vec3,
        interactionRange: Double,
    ): InteractableResolvedInteraction?
}

internal data class InteractableResolvedInteraction(
    val point: Vec3,
    val interact: (InteractionHand) -> InteractionResult?,
)

internal sealed interface InteractableRuntimeStatus {
    data class State(val state: InteractableSessionState) : InteractableRuntimeStatus
    data class Failure(val reason: String) : InteractableRuntimeStatus
    data class Recovery(val cause: InteractableSessionCause) : InteractableRuntimeStatus
    data class Terminated(val cause: InteractableSessionCause) : InteractableRuntimeStatus
    data class RecoveryStalled(val cause: InteractableSessionCause) : InteractableRuntimeStatus
    data class Resynchronized(val position: Vec3) : InteractableRuntimeStatus
}

internal fun interactWithVanillaHandOrder(
    interact: (InteractionHand) -> InteractionResult?,
): Boolean {
    for (hand in InteractionHand.entries) {
        when (interact(hand)) {
            is InteractionResult.Success, is InteractionResult.Fail -> return true
            else -> Unit
        }
    }
    return false
}

internal fun interactionDeliveryConfirmed(
    handled: Boolean,
    dispositions: List<InteractablePacketDisposition>,
): Boolean = handled && dispositions.isNotEmpty() &&
    dispositions.all { it == InteractablePacketDisposition.DELIVERED }

internal fun shouldRewriteInteractableAmbientMovement(
    movementLeaseRequired: Boolean,
    correctionInProgress: Boolean,
): Boolean = movementLeaseRequired && !correctionInProgress
