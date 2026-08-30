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

internal fun <Stack> updateShieldInterrupting(
    current: SpearShieldState.Interrupting<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> {
    val session = current.session.rememberShieldKey(observation.useKeyDown)
    invalidShieldInventory(session, observation, stopUse = false)?.let { return it }
    if (!observation.threatPresent || !observation.aligned) {
        return stopShieldBeforeBlocking(session, observation)
    }
    if (observation.usingItem) return unchangedShieldState(SpearShieldState.Interrupting(session))
    return beginShieldAcquisition(session, observation.tick)
}

internal fun <Stack> updateShieldEquipping(
    current: SpearShieldState.Equipping<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> {
    val session = current.session.rememberShieldKey(observation.useKeyDown)
    return when (observation.inventoryLayout) {
        SpearShieldInventoryLayout.ORIGINAL -> unchangedShieldState(SpearShieldState.Equipping(session))
        SpearShieldInventoryLayout.EQUIPPED -> {
            if (!observation.threatPresent || !observation.aligned) {
                lowerShield(session, observation.tick, stopUse = false)
            } else {
                startShieldBlocking(session, observation.tick)
            }
        }
        SpearShieldInventoryLayout.SHIELD_BROKEN -> restoreBrokenShield(session, stopUse = false)
        SpearShieldInventoryLayout.RESTORED_AFTER_BREAK -> abortShield(
            session,
            SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT,
            stopUse = false,
        )
        SpearShieldInventoryLayout.CHANGED -> abortShield(
            session,
            SpearShieldAbortReason.INVENTORY_CHANGED,
            stopUse = false,
        )
        SpearShieldInventoryLayout.NOT_REQUIRED -> abortShield(
            session,
            SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT,
            stopUse = false,
        )
    }
}

internal fun <Stack> updateShieldBlocking(
    current: SpearShieldState.Blocking<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> {
    val session = current.session.transferManualShieldUseOnFreshPress(observation.useKeyDown)
    val invalidInventory = invalidShieldInventory(session, observation, stopUse = true)
    return when {
        invalidInventory != null -> invalidInventory
        observation.inventoryLayout == SpearShieldInventoryLayout.SHIELD_BROKEN -> {
            restoreBrokenShield(session, stopUse = true)
        }
        !observation.threatPresent || !observation.aligned -> lowerShield(
            session = session,
            tick = observation.tick,
            stopUse = session.useOwnership == SpearShieldUseOwnership.MODULE,
            waitForManualUse = session.useOwnership == SpearShieldUseOwnership.MANUAL &&
                (observation.shieldUseActive || observation.useKeyDown),
        )
        session.useOwnership == SpearShieldUseOwnership.MANUAL -> {
            if (observation.shieldUseActive || observation.useKeyDown) {
                unchangedShieldState(current.copy(session = session))
            } else {
                startShieldBlocking(session.copy(useOwnership = SpearShieldUseOwnership.MODULE), observation.tick)
            }
        }
        observation.shieldUseActive -> unchangedShieldState(current.copy(session = session))
        else -> startShieldBlocking(session, observation.tick)
    }
}

internal fun <Stack> updateShieldLowered(
    current: SpearShieldState.LoweredAwaitingRestore<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> {
    val session = current.session.rememberShieldKey(observation.useKeyDown)
    val invalidInventory = invalidShieldInventory(session, observation, stopUse = false)
    return when {
        invalidInventory != null -> invalidInventory
        canRaiseShieldAgain(session, current, observation) -> {
            resumeShieldBlocking(current, session, observation)
        }
        current.restoreAtTick == null -> {
            awaitShieldRestore(current, session, observation)
        }
        observation.tick < current.restoreAtTick -> unchangedShieldState(current.copy(session = session))
        else -> beginShieldRestore(session, observation.inventoryLayout)
    }
}

private fun <Stack> resumeShieldBlocking(
    current: SpearShieldState.LoweredAwaitingRestore<Stack>,
    session: SpearShieldSession<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> =
    if (current.session.useOwnership == SpearShieldUseOwnership.MANUAL &&
        (observation.shieldUseActive || observation.useKeyDown)
    ) {
        SpearShieldTransition(SpearShieldState.Blocking(session, observation.tick))
    } else {
        startShieldBlocking(session.copy(useOwnership = SpearShieldUseOwnership.MODULE), observation.tick)
    }

private fun <Stack> awaitShieldRestore(
    current: SpearShieldState.LoweredAwaitingRestore<Stack>,
    session: SpearShieldSession<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> =
    if (observation.shieldUseActive || observation.useKeyDown) {
        unchangedShieldState(current.copy(session = session))
    } else {
        unchangedShieldState(
            current.copy(session = session, restoreAtTick = observation.tick + session.policy.releaseDelayTicks),
        )
    }

internal fun <Stack> updateShieldRestoring(
    current: SpearShieldState.Restoring<Stack>,
    observation: SpearShieldObservation,
): SpearShieldTransition<Stack> {
    val restorationComplete = when (current.kind) {
        SpearShieldRestoreKind.STANDARD -> observation.inventoryLayout == SpearShieldInventoryLayout.ORIGINAL
        SpearShieldRestoreKind.AFTER_SHIELD_BREAK ->
            observation.inventoryLayout == SpearShieldInventoryLayout.RESTORED_AFTER_BREAK
    }
    if (restorationComplete) {
        return SpearShieldTransition(
            SpearShieldState.Idle,
            listOf(SpearShieldCommand.ReleaseOffhandReservation),
        )
    }
    if (observation.inventoryLayout == SpearShieldInventoryLayout.EQUIPPED ||
        observation.inventoryLayout == SpearShieldInventoryLayout.SHIELD_BROKEN) {
        return unchangedShieldState(current)
    }
    return abortShield(current.session, SpearShieldAbortReason.INVENTORY_CHANGED, stopUse = false)
}
