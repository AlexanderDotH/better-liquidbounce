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

/** Pure facade for shield ownership and inventory restoration transitions. */
object SpearShieldController {
    fun <Stack> acquire(
        current: SpearShieldState<Stack>,
        request: SpearShieldAcquisition<Stack>,
    ): SpearShieldTransition<Stack> {
        if (current !is SpearShieldState.Idle && current !is SpearShieldState.Aborted) {
            return unchangedShieldState(current)
        }
        val route = request.route ?: return unchangedShieldState(current)
        if (!request.aligned || request.usingShield) return unchangedShieldState(current)

        val session = SpearShieldSession(
            route = route,
            policy = request.policy,
            useOwnership = SpearShieldUseOwnership.MODULE,
            previousUseKeyDown = request.useKeyDown,
        )
        val reservation = shieldReservationCommand(route)
        if (request.usingItem) {
            return SpearShieldTransition(
                SpearShieldState.Interrupting(session),
                reservation + SpearShieldCommand.ReleaseItemUse,
            )
        }
        return beginShieldAcquisition(session, request.tick, reservation)
    }

    fun <Stack> update(
        current: SpearShieldState<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> = when (current) {
        SpearShieldState.Idle -> unchangedShieldState(current)
        is SpearShieldState.Interrupting -> updateShieldInterrupting(current, observation)
        is SpearShieldState.Equipping -> updateShieldEquipping(current, observation)
        is SpearShieldState.Blocking -> updateShieldBlocking(current, observation)
        is SpearShieldState.LoweredAwaitingRestore -> updateShieldLowered(current, observation)
        is SpearShieldState.Restoring -> updateShieldRestoring(current, observation)
        is SpearShieldState.Aborted -> unchangedShieldState(current)
    }

    fun <Stack> disable(
        current: SpearShieldState<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> = when (current) {
        SpearShieldState.Idle -> unchangedShieldState(current)
        is SpearShieldState.Aborted -> unchangedShieldState(current)
        is SpearShieldState.Interrupting -> stopShieldBeforeBlocking(current.session, observation)
        is SpearShieldState.Equipping -> stopShieldBeforeBlocking(current.session, observation)
        is SpearShieldState.Blocking -> disableShieldBlocking(current, observation)
        is SpearShieldState.LoweredAwaitingRestore -> disableShieldLowered(current, observation)
        is SpearShieldState.Restoring -> updateShieldRestoring(current, observation)
    }

    fun <Stack> worldReset(): SpearShieldTransition<Stack> = SpearShieldTransition(SpearShieldState.Idle)
}
