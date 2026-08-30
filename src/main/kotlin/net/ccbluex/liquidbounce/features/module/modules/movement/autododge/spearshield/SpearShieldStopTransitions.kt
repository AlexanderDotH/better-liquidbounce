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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge.spearshield

internal fun <Stack> disableShieldBlocking(
    current: SpearShieldState.Blocking<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> {
    val session = current.session.transferManualShieldUseOnFreshPress(observation.useKeyDown)
    if (session.useOwnership == SpearShieldUseOwnership.MANUAL &&
        (observation.shieldUseActive || observation.useKeyDown)) {
        return SpearShieldTransition(SpearShieldState.LoweredAwaitingRestore(session, restoreAtTick = null))
    }
    return stopAndRestoreShield(session, observation, stopUse = true)
}

internal fun <Stack> disableShieldLowered(
    current: SpearShieldState.LoweredAwaitingRestore<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> {
    val session = current.session.rememberShieldKey(observation.useKeyDown)
    if (session.useOwnership == SpearShieldUseOwnership.MANUAL &&
        (observation.shieldUseActive || observation.useKeyDown)) {
        return unchangedShieldState(current.copy(session = session, restoreAtTick = null))
    }
    return stopAndRestoreShield(session, observation, stopUse = false)
}

internal fun <Stack> stopShieldBeforeBlocking(
    session: SpearShieldSession<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> = when (session.route) {
    is SpearShieldRoute.AlreadyEquipped -> SpearShieldTransition(SpearShieldState.Idle)
    is SpearShieldRoute.SwapToOffhand -> when (observation.inventoryLayout) {
        SpearShieldInventoryLayout.ORIGINAL,
        SpearShieldInventoryLayout.NOT_REQUIRED -> SpearShieldTransition(
            SpearShieldState.Idle,
            listOf(SpearShieldCommand.ReleaseOffhandReservation),
        )
        SpearShieldInventoryLayout.EQUIPPED,
        SpearShieldInventoryLayout.SHIELD_BROKEN -> beginShieldRestore(session, observation.inventoryLayout)
        SpearShieldInventoryLayout.RESTORED_AFTER_BREAK,
        SpearShieldInventoryLayout.CHANGED -> abortShield(
            session,
            SpearShieldAbortReason.INVENTORY_CHANGED,
            stopUse = false,
        )
    }
}

internal fun <Stack> stopAndRestoreShield(
    session: SpearShieldSession<Stack>,
    observation: SpearShieldObservation,
    stopUse: Boolean,
): SpearShieldTransition<Stack> {
    if (session.route is SpearShieldRoute.AlreadyEquipped) {
        val commands = if (stopUse) listOf(SpearShieldCommand.StopShieldUse) else emptyList()
        return SpearShieldTransition(SpearShieldState.Idle, commands)
    }
    if (observation.inventoryLayout == SpearShieldInventoryLayout.CHANGED) {
        return abortShield(session, SpearShieldAbortReason.INVENTORY_CHANGED, stopUse)
    }
    if (observation.inventoryLayout == SpearShieldInventoryLayout.ORIGINAL) {
        val commands = buildList {
            if (stopUse) add(SpearShieldCommand.StopShieldUse)
            add(SpearShieldCommand.ReleaseOffhandReservation)
        }
        return SpearShieldTransition(SpearShieldState.Idle, commands)
    }
    if (observation.inventoryLayout != SpearShieldInventoryLayout.EQUIPPED &&
        observation.inventoryLayout != SpearShieldInventoryLayout.SHIELD_BROKEN) {
        return abortShield(session, SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT, stopUse)
    }
    val snapshot = session.shieldSwapSnapshot()
        ?: return abortShield(session, SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT, stopUse)
    val commands = buildList {
        if (stopUse) add(SpearShieldCommand.StopShieldUse)
        add(SpearShieldCommand.RestoreOffhand(snapshot))
    }
    val restoreKind = if (observation.inventoryLayout == SpearShieldInventoryLayout.SHIELD_BROKEN) {
        SpearShieldRestoreKind.AFTER_SHIELD_BREAK
    } else {
        SpearShieldRestoreKind.STANDARD
    }
    return SpearShieldTransition(SpearShieldState.Restoring(session, restoreKind), commands)
}
