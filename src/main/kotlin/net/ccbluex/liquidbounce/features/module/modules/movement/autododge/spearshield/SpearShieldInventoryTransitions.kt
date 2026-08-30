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

internal fun <Stack> invalidShieldInventory(
    session: SpearShieldSession<Stack>,
    observation: SpearShieldObservation,
    stopUse: Boolean,
): SpearShieldTransition<Stack>? {
    if (session.route is SpearShieldRoute.AlreadyEquipped) return null
    if (observation.inventoryLayout != SpearShieldInventoryLayout.CHANGED &&
        observation.inventoryLayout != SpearShieldInventoryLayout.NOT_REQUIRED &&
        observation.inventoryLayout != SpearShieldInventoryLayout.RESTORED_AFTER_BREAK) {
        return null
    }
    return abortShield(session, SpearShieldAbortReason.INVENTORY_CHANGED, stopUse)
}

internal fun <Stack> restoreBrokenShield(
    session: SpearShieldSession<Stack>,
    stopUse: Boolean,
): SpearShieldTransition<Stack> {
    val snapshot = session.shieldSwapSnapshot()
        ?: return abortShield(session, SpearShieldAbortReason.SHIELD_BROKEN, stopUse)
    val commands = buildList {
        if (stopUse && session.useOwnership == SpearShieldUseOwnership.MODULE) {
            add(SpearShieldCommand.StopShieldUse)
        }
        add(SpearShieldCommand.RestoreOffhand(snapshot))
    }
    return SpearShieldTransition(
        SpearShieldState.Restoring(session, SpearShieldRestoreKind.AFTER_SHIELD_BREAK),
        commands,
    )
}

internal fun <Stack> beginShieldRestore(
    session: SpearShieldSession<Stack>,
    layout: SpearShieldInventoryLayout,
): SpearShieldTransition<Stack> {
    if (session.route is SpearShieldRoute.AlreadyEquipped) {
        return SpearShieldTransition(SpearShieldState.Idle)
    }
    if (layout == SpearShieldInventoryLayout.ORIGINAL) {
        return SpearShieldTransition(
            SpearShieldState.Idle,
            listOf(SpearShieldCommand.ReleaseOffhandReservation),
        )
    }
    if (layout != SpearShieldInventoryLayout.EQUIPPED && layout != SpearShieldInventoryLayout.SHIELD_BROKEN) {
        return abortShield(session, SpearShieldAbortReason.INVENTORY_CHANGED, stopUse = false)
    }
    val snapshot = session.shieldSwapSnapshot()
        ?: return abortShield(session, SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT, stopUse = false)
    val restoreKind = if (layout == SpearShieldInventoryLayout.SHIELD_BROKEN) {
        SpearShieldRestoreKind.AFTER_SHIELD_BREAK
    } else {
        SpearShieldRestoreKind.STANDARD
    }
    return SpearShieldTransition(
        SpearShieldState.Restoring(session, restoreKind),
        listOf(SpearShieldCommand.RestoreOffhand(snapshot)),
    )
}

internal fun <Stack> abortShield(
    session: SpearShieldSession<Stack>,
    reason: SpearShieldAbortReason,
    stopUse: Boolean,
): SpearShieldTransition<Stack> {
    val commands = buildList {
        if (stopUse && session.useOwnership == SpearShieldUseOwnership.MODULE) {
            add(SpearShieldCommand.StopShieldUse)
        }
        if (session.route is SpearShieldRoute.SwapToOffhand) {
            add(SpearShieldCommand.ReleaseOffhandReservation)
        }
    }
    return SpearShieldTransition(SpearShieldState.Aborted(session, reason), commands)
}
