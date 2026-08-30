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

internal fun <Stack> beginShieldAcquisition(
    session: SpearShieldSession<Stack>,
    tick: Long,
    precedingCommands: List<SpearShieldCommand<Stack>> = emptyList(),
): SpearShieldTransition<Stack> = when (val route = session.route) {
    is SpearShieldRoute.AlreadyEquipped -> SpearShieldTransition(
        SpearShieldState.Blocking(session, tick),
        precedingCommands + SpearShieldCommand.StartShieldUse(route.hand),
    )
    is SpearShieldRoute.SwapToOffhand -> SpearShieldTransition(
        SpearShieldState.Equipping(session),
        precedingCommands + SpearShieldCommand.SwapIntoOffhand(route.snapshot),
    )
}

internal fun <Stack> startShieldBlocking(
    session: SpearShieldSession<Stack>,
    tick: Long,
): SpearShieldTransition<Stack> {
    val hand = when (session.route) {
        is SpearShieldRoute.AlreadyEquipped -> session.route.hand
        is SpearShieldRoute.SwapToOffhand -> SpearShieldHand.OFF_HAND
    }
    return SpearShieldTransition(
        SpearShieldState.Blocking(session, tick),
        listOf(SpearShieldCommand.StartShieldUse(hand)),
    )
}

internal fun <Stack> lowerShield(
    session: SpearShieldSession<Stack>,
    tick: Long,
    stopUse: Boolean,
    waitForManualUse: Boolean = false,
): SpearShieldTransition<Stack> {
    val restoreAtTick = if (waitForManualUse) null else tick + session.policy.releaseDelayTicks
    val commands = if (stopUse) listOf(SpearShieldCommand.StopShieldUse) else emptyList()
    return SpearShieldTransition(
        SpearShieldState.LoweredAwaitingRestore(session, restoreAtTick),
        commands,
    )
}

internal fun <Stack> canRaiseShieldAgain(
    session: SpearShieldSession<Stack>,
    current: SpearShieldState.LoweredAwaitingRestore<Stack>,
    observation: SpearShieldObservation,
): Boolean {
    if (!observation.threatPresent || !observation.aligned) return false
    if (current.restoreAtTick != null && observation.tick >= current.restoreAtTick) return false
    return session.route is SpearShieldRoute.AlreadyEquipped ||
        observation.inventoryLayout == SpearShieldInventoryLayout.EQUIPPED
}

internal fun <Stack> shieldReservationCommand(
    route: SpearShieldRoute<Stack>,
): List<SpearShieldCommand<Stack>> = if (route is SpearShieldRoute.SwapToOffhand) {
    listOf(SpearShieldCommand.ReserveOffhand)
} else {
    emptyList()
}

internal fun <Stack> SpearShieldSession<Stack>.rememberShieldKey(useKeyDown: Boolean) =
    copy(previousUseKeyDown = useKeyDown)

internal fun <Stack> SpearShieldSession<Stack>.transferManualShieldUseOnFreshPress(
    useKeyDown: Boolean,
): SpearShieldSession<Stack> = copy(
    useOwnership = if (!previousUseKeyDown && useKeyDown) SpearShieldUseOwnership.MANUAL else useOwnership,
    previousUseKeyDown = useKeyDown,
)

internal fun <Stack> SpearShieldSession<Stack>.shieldSwapSnapshot(): SpearShieldInventorySnapshot<Stack>? =
    (route as? SpearShieldRoute.SwapToOffhand)?.snapshot

internal fun <Stack> unchangedShieldState(state: SpearShieldState<Stack>) = SpearShieldTransition(state)
