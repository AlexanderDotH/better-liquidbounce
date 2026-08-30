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
package net.ccbluex.liquidbounce.features.baritone

import net.ccbluex.liquidbounce.features.baritone.adapter.BaritoneConflictDetector
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseCause
import net.ccbluex.liquidbounce.features.baritone.core.BaritonePauseReason
import net.ccbluex.liquidbounce.features.baritone.flight.runtime.BaritoneFlightLeaseRegistry
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.movement.remote.RemoteMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBaritone
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.features.inventory.InventoryManager

internal object LiquidBounceBaritoneConflictDetector : BaritoneConflictDetector {

    override fun detect(): Collection<BaritonePauseCause> = collectLiquidBouncePauseCauses(
        userInput = ModuleBaritone.pauseOnUserInput && physicalControlInput(),
        rotationOwned = RotationManager.currentRotation != null,
        hotbarOwned = SilentHotbar.isSlotModified(),
        inventoryOwned = InventoryManager.isInventoryOpen,
        blinkActive = BlinkManager.isLagging,
        remoteMovementOwned = RemoteMovementOwnership.active,
        movementOwners = ModuleBaritone.conflictModuleNames.filter { ModuleManager[it]?.running == true },
        exemptLeasedFly = BaritoneFlightLeaseRegistry.exemptsFlyConflict(),
    )

    private fun physicalControlInput(): Boolean = with(mc.options) {
        listOf(
            keyUp,
            keyDown,
            keyLeft,
            keyRight,
            keyJump,
            keyShift,
            keySprint,
            keyAttack,
            keyUse,
            keyPickItem,
        ).any { it.isPressedOnAny }
    }
}

@Suppress("LongParameterList")
internal fun collectLiquidBouncePauseCauses(
    userInput: Boolean,
    rotationOwned: Boolean,
    hotbarOwned: Boolean,
    inventoryOwned: Boolean,
    blinkActive: Boolean,
    remoteMovementOwned: Boolean,
    movementOwners: Collection<String>,
    exemptLeasedFly: Boolean = false,
): List<BaritonePauseCause> = buildList {
    if (userInput) add(BaritonePauseCause(BaritonePauseReason.USER_INPUT))
    if (rotationOwned) add(BaritonePauseCause(BaritonePauseReason.ROTATION_OWNER, "RotationManager"))
    if (hotbarOwned) add(BaritonePauseCause(BaritonePauseReason.HOTBAR_OWNER, "SilentHotbar"))
    if (inventoryOwned) add(BaritonePauseCause(BaritonePauseReason.INVENTORY_OWNER, "InventoryManager"))
    if (blinkActive) add(BaritonePauseCause(BaritonePauseReason.MOVEMENT_OWNER, "Blink"))
    if (remoteMovementOwned) {
        add(BaritonePauseCause(BaritonePauseReason.REMOTE_MOVEMENT_OWNER, "RemoteKill"))
    }
    movementOwners.asSequence()
        .filter(String::isNotBlank)
        .filterNot { exemptLeasedFly && it.equals("Fly", ignoreCase = true) }
        .distinct()
        .sorted()
        .mapTo(this) { BaritonePauseCause(BaritonePauseReason.MOVEMENT_OWNER, it) }
}
