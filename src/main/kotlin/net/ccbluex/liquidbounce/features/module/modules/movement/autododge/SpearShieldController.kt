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
package net.ccbluex.liquidbounce.features.module.modules.movement.autododge

enum class SpearShieldHand {
    MAIN_HAND,
    OFF_HAND,
}

enum class SpearShieldInventoryLayout {
    /** No swap route exists, so inventory validation is not needed. */
    NOT_REQUIRED,

    /** The source and offhand still contain their snapshotted stacks. */
    ORIGINAL,

    /** The exact snapshotted stacks have exchanged places. */
    EQUIPPED,

    /** The displaced offhand stack is at the source and the shield has broken. */
    SHIELD_BROKEN,

    /** The displaced offhand stack was restored after the shield broke, leaving the source empty. */
    RESTORED_AFTER_BREAK,

    /** The container or either stack no longer matches a safe known layout. */
    CHANGED,
}

data class SpearShieldInventorySnapshot<out Stack>(
    val containerId: Int,
    val sourceSlot: Int,
    val shieldStack: Stack,
    val displacedOffhandStack: Stack,
) {
    init {
        require(sourceSlot >= 0)
    }

    /**
     * Classifies both slots together. Restoration is allowed only from [SpearShieldInventoryLayout.EQUIPPED]
     * or the exact broken-shield layout; every other mutation is treated as user/server ownership.
     */
    inline fun classify(
        containerId: Int,
        sourceStack: @UnsafeVariance Stack,
        offhandStack: @UnsafeVariance Stack,
        stacksMatch: (Stack, Stack) -> Boolean,
        isEmpty: (Stack) -> Boolean,
        expectBrokenShieldRestored: Boolean = false,
    ): SpearShieldInventoryLayout {
        if (this.containerId != containerId) {
            return SpearShieldInventoryLayout.CHANGED
        }

        if (stacksMatch(sourceStack, shieldStack) && stacksMatch(offhandStack, displacedOffhandStack)) {
            return SpearShieldInventoryLayout.ORIGINAL
        }

        if (stacksMatch(sourceStack, displacedOffhandStack) && stacksMatch(offhandStack, shieldStack)) {
            return SpearShieldInventoryLayout.EQUIPPED
        }

        if (expectBrokenShieldRestored && isEmpty(sourceStack) && stacksMatch(offhandStack, displacedOffhandStack)) {
            return SpearShieldInventoryLayout.RESTORED_AFTER_BREAK
        }

        if (stacksMatch(sourceStack, displacedOffhandStack) && isEmpty(offhandStack)) {
            return SpearShieldInventoryLayout.SHIELD_BROKEN
        }

        return SpearShieldInventoryLayout.CHANGED
    }
}

sealed interface SpearShieldRoute<out Stack> {
    data class AlreadyEquipped(val hand: SpearShieldHand) : SpearShieldRoute<Nothing>

    data class SwapToOffhand<Stack>(
        val snapshot: SpearShieldInventorySnapshot<Stack>,
    ) : SpearShieldRoute<Stack>
}

enum class SpearShieldUseOwnership {
    MODULE,
    MANUAL,
}

data class SpearShieldSession<out Stack>(
    val route: SpearShieldRoute<Stack>,
    val policy: SpearShieldPolicy,
    val useOwnership: SpearShieldUseOwnership,
    val previousUseKeyDown: Boolean,
)

sealed interface SpearShieldState<out Stack> {
    data object Idle : SpearShieldState<Nothing>

    data class Interrupting<Stack>(val session: SpearShieldSession<Stack>) : SpearShieldState<Stack>

    data class Equipping<Stack>(val session: SpearShieldSession<Stack>) : SpearShieldState<Stack>

    data class Blocking<Stack>(
        val session: SpearShieldSession<Stack>,
        val useStartedAtTick: Long,
    ) : SpearShieldState<Stack> {
        val blockReadyAtTick: Long
            get() = useStartedAtTick + session.policy.blockDelayTicks
    }

    data class LoweredAwaitingRestore<Stack>(
        val session: SpearShieldSession<Stack>,
        /** Null while a manually-owned use must be allowed to finish. */
        val restoreAtTick: Long?,
    ) : SpearShieldState<Stack>

    data class Restoring<Stack>(
        val session: SpearShieldSession<Stack>,
        val kind: SpearShieldRestoreKind,
    ) : SpearShieldState<Stack>

    data class Aborted<Stack>(
        val session: SpearShieldSession<Stack>,
        val reason: SpearShieldAbortReason,
    ) : SpearShieldState<Stack>
}

internal fun shouldPreserveAutoDodgeShieldUse(state: SpearShieldState<*>): Boolean =
    state is SpearShieldState.Blocking && state.session.useOwnership == SpearShieldUseOwnership.MODULE

internal fun shouldSuppressAutoDodgeVanillaUse(state: SpearShieldState<*>): Boolean {
    val session = when (state) {
        SpearShieldState.Idle,
        is SpearShieldState.Aborted -> return false
        is SpearShieldState.Interrupting -> state.session
        is SpearShieldState.Equipping -> state.session
        is SpearShieldState.Blocking -> state.session
        is SpearShieldState.LoweredAwaitingRestore -> state.session
        is SpearShieldState.Restoring -> state.session
    }

    return session.useOwnership == SpearShieldUseOwnership.MODULE
}

enum class SpearShieldAbortReason {
    INVENTORY_CHANGED,
    SHIELD_BROKEN,
    INVALID_INVENTORY_LAYOUT,
}

enum class SpearShieldRestoreKind {
    STANDARD,
    AFTER_SHIELD_BREAK,
}

data class SpearShieldAcquisition<out Stack>(
    val tick: Long,
    val aligned: Boolean,
    /** Null means the adapter found no verified, usable acquisition route. */
    val route: SpearShieldRoute<Stack>?,
    val usingItem: Boolean,
    /** True means a shield was already in use before AutoDodge and must not be claimed. */
    val usingShield: Boolean,
    val useKeyDown: Boolean,
    val policy: SpearShieldPolicy,
)

data class SpearShieldObservation(
    val tick: Long,
    val threatPresent: Boolean,
    val aligned: Boolean,
    val usingItem: Boolean,
    val shieldUseActive: Boolean,
    val useKeyDown: Boolean,
    val inventoryLayout: SpearShieldInventoryLayout,
)

sealed interface SpearShieldCommand<out Stack> {
    data object ReserveOffhand : SpearShieldCommand<Nothing>

    data object ReleaseItemUse : SpearShieldCommand<Nothing>

    data class SwapIntoOffhand<Stack>(
        val snapshot: SpearShieldInventorySnapshot<Stack>,
    ) : SpearShieldCommand<Stack>

    data class StartShieldUse(val hand: SpearShieldHand) : SpearShieldCommand<Nothing>

    data object StopShieldUse : SpearShieldCommand<Nothing>

    data class RestoreOffhand<Stack>(
        val snapshot: SpearShieldInventorySnapshot<Stack>,
    ) : SpearShieldCommand<Stack>

    data object ReleaseOffhandReservation : SpearShieldCommand<Nothing>
}

data class SpearShieldTransition<out Stack>(
    val state: SpearShieldState<Stack>,
    val commands: List<SpearShieldCommand<Stack>> = emptyList(),
)

/**
 * Pure reducer for shield ownership and inventory restoration. Commands are edge-triggered: the adapter executes
 * them once and acknowledges inventory commands through the next [SpearShieldObservation].
 */
@Suppress("TooManyFunctions")
object SpearShieldController {

    fun <Stack> acquire(
        current: SpearShieldState<Stack>,
        request: SpearShieldAcquisition<Stack>,
    ): SpearShieldTransition<Stack> {
        if (current !is SpearShieldState.Idle && current !is SpearShieldState.Aborted) {
            return unchanged(current)
        }

        val route = request.route ?: return unchanged(current)
        if (!request.aligned || request.usingShield) {
            return unchanged(current)
        }

        val session = SpearShieldSession(
            route = route,
            policy = request.policy,
            useOwnership = SpearShieldUseOwnership.MODULE,
            previousUseKeyDown = request.useKeyDown,
        )
        val reservation = reservationCommand(route)

        if (request.usingItem) {
            return SpearShieldTransition(
                SpearShieldState.Interrupting(session),
                reservation + SpearShieldCommand.ReleaseItemUse,
            )
        }

        return beginAcquisition(session, request.tick, reservation)
    }

    fun <Stack> update(
        current: SpearShieldState<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> = when (current) {
        SpearShieldState.Idle -> unchanged(current)
        is SpearShieldState.Interrupting -> updateInterrupting(current, observation)
        is SpearShieldState.Equipping -> updateEquipping(current, observation)
        is SpearShieldState.Blocking -> updateBlocking(current, observation)
        is SpearShieldState.LoweredAwaitingRestore -> updateLowered(current, observation)
        is SpearShieldState.Restoring -> updateRestoring(current, observation)
        is SpearShieldState.Aborted -> unchanged(current)
    }

    fun <Stack> disable(
        current: SpearShieldState<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> = when (current) {
        SpearShieldState.Idle -> unchanged(current)
        is SpearShieldState.Aborted -> unchanged(current)
        is SpearShieldState.Interrupting -> stopBeforeBlocking(current.session, observation)
        is SpearShieldState.Equipping -> stopBeforeBlocking(current.session, observation)
        is SpearShieldState.Blocking -> disableBlocking(current, observation)
        is SpearShieldState.LoweredAwaitingRestore -> disableLowered(current, observation)
        is SpearShieldState.Restoring -> updateRestoring(current, observation)
    }

    fun <Stack> worldReset(): SpearShieldTransition<Stack> = SpearShieldTransition(SpearShieldState.Idle)

    private fun <Stack> updateInterrupting(
        current: SpearShieldState.Interrupting<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> {
        val session = current.session.rememberKey(observation.useKeyDown)
        invalidInventory(session, observation, stopUse = false)?.let { return it }

        if (!observation.threatPresent || !observation.aligned) {
            return stopBeforeBlocking(session, observation)
        }

        if (observation.usingItem) {
            return unchanged(SpearShieldState.Interrupting(session))
        }

        return beginAcquisition(session, observation.tick)
    }

    private fun <Stack> updateEquipping(
        current: SpearShieldState.Equipping<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> {
        val session = current.session.rememberKey(observation.useKeyDown)

        return when (observation.inventoryLayout) {
            SpearShieldInventoryLayout.ORIGINAL -> unchanged(SpearShieldState.Equipping(session))
            SpearShieldInventoryLayout.EQUIPPED -> {
                if (!observation.threatPresent || !observation.aligned) {
                    lower(session, observation.tick, stopUse = false)
                } else {
                    startBlocking(session, observation.tick)
                }
            }
            SpearShieldInventoryLayout.SHIELD_BROKEN -> restoreBrokenShield(session, stopUse = false)
            SpearShieldInventoryLayout.RESTORED_AFTER_BREAK -> abort(
                session,
                SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT,
                stopUse = false,
            )
            SpearShieldInventoryLayout.CHANGED -> abort(
                session,
                SpearShieldAbortReason.INVENTORY_CHANGED,
                stopUse = false,
            )
            SpearShieldInventoryLayout.NOT_REQUIRED -> abort(
                session,
                SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT,
                stopUse = false,
            )
        }
    }

    @Suppress("ReturnCount")
    private fun <Stack> updateBlocking(
        current: SpearShieldState.Blocking<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> {
        val session = current.session.transferManualUseOnFreshPress(observation.useKeyDown)
        invalidInventory(session, observation, stopUse = true)?.let { return it }

        if (observation.inventoryLayout == SpearShieldInventoryLayout.SHIELD_BROKEN) {
            return restoreBrokenShield(session, stopUse = true)
        }

        if (!observation.threatPresent || !observation.aligned) {
            return lower(
                session = session,
                tick = observation.tick,
                stopUse = session.useOwnership == SpearShieldUseOwnership.MODULE,
                waitForManualUse = session.useOwnership == SpearShieldUseOwnership.MANUAL &&
                    (observation.shieldUseActive || observation.useKeyDown),
            )
        }

        if (session.useOwnership == SpearShieldUseOwnership.MANUAL) {
            if (observation.shieldUseActive || observation.useKeyDown) {
                return unchanged(current.copy(session = session))
            }

            return startBlocking(session.copy(useOwnership = SpearShieldUseOwnership.MODULE), observation.tick)
        }

        if (observation.shieldUseActive) {
            return unchanged(current.copy(session = session))
        }

        return startBlocking(session, observation.tick)
    }

    @Suppress("ReturnCount")
    private fun <Stack> updateLowered(
        current: SpearShieldState.LoweredAwaitingRestore<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> {
        val session = current.session.rememberKey(observation.useKeyDown)
        invalidInventory(session, observation, stopUse = false)?.let { return it }

        if (canRaiseAgain(session, current, observation)) {
            if (current.session.useOwnership == SpearShieldUseOwnership.MANUAL &&
                (observation.shieldUseActive || observation.useKeyDown)) {
                return SpearShieldTransition(
                    SpearShieldState.Blocking(session, observation.tick),
                )
            }

            return startBlocking(session.copy(useOwnership = SpearShieldUseOwnership.MODULE), observation.tick)
        }

        if (current.restoreAtTick == null) {
            if (observation.shieldUseActive || observation.useKeyDown) {
                return unchanged(current.copy(session = session))
            }

            return unchanged(
                current.copy(
                    session = session,
                    restoreAtTick = observation.tick + session.policy.releaseDelayTicks,
                ),
            )
        }

        if (observation.tick < current.restoreAtTick) {
            return unchanged(current.copy(session = session))
        }

        return beginRestore(session, observation.inventoryLayout)
    }

    private fun <Stack> updateRestoring(
        current: SpearShieldState.Restoring<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> {
        val restorationComplete = when (current.kind) {
            SpearShieldRestoreKind.STANDARD ->
                observation.inventoryLayout == SpearShieldInventoryLayout.ORIGINAL
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
            return unchanged(current)
        }

        return abort(
            current.session,
            SpearShieldAbortReason.INVENTORY_CHANGED,
            stopUse = false,
        )
    }

    private fun <Stack> disableBlocking(
        current: SpearShieldState.Blocking<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> {
        val session = current.session.transferManualUseOnFreshPress(observation.useKeyDown)
        if (session.useOwnership == SpearShieldUseOwnership.MANUAL &&
            (observation.shieldUseActive || observation.useKeyDown)) {
            return SpearShieldTransition(SpearShieldState.LoweredAwaitingRestore(session, restoreAtTick = null))
        }

        return stopAndRestore(session, observation, stopUse = true)
    }

    private fun <Stack> disableLowered(
        current: SpearShieldState.LoweredAwaitingRestore<Stack>,
        observation: SpearShieldObservation,
    ): SpearShieldTransition<Stack> {
        val session = current.session.rememberKey(observation.useKeyDown)
        if (session.useOwnership == SpearShieldUseOwnership.MANUAL &&
            (observation.shieldUseActive || observation.useKeyDown)) {
            return unchanged(current.copy(session = session, restoreAtTick = null))
        }

        return stopAndRestore(session, observation, stopUse = false)
    }

    private fun <Stack> stopBeforeBlocking(
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
            SpearShieldInventoryLayout.SHIELD_BROKEN -> beginRestore(session, observation.inventoryLayout)
            SpearShieldInventoryLayout.RESTORED_AFTER_BREAK -> abort(
                session,
                SpearShieldAbortReason.INVENTORY_CHANGED,
                stopUse = false,
            )
            SpearShieldInventoryLayout.CHANGED -> abort(
                session,
                SpearShieldAbortReason.INVENTORY_CHANGED,
                stopUse = false,
            )
        }
    }

    private fun <Stack> stopAndRestore(
        session: SpearShieldSession<Stack>,
        observation: SpearShieldObservation,
        stopUse: Boolean,
    ): SpearShieldTransition<Stack> {
        if (session.route is SpearShieldRoute.AlreadyEquipped) {
            val commands = if (stopUse) listOf(SpearShieldCommand.StopShieldUse) else emptyList()
            return SpearShieldTransition(SpearShieldState.Idle, commands)
        }

        if (observation.inventoryLayout == SpearShieldInventoryLayout.CHANGED) {
            return abort(session, SpearShieldAbortReason.INVENTORY_CHANGED, stopUse)
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
            return abort(session, SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT, stopUse)
        }

        val snapshot = session.swapSnapshot()
            ?: return abort(session, SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT, stopUse)
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

    private fun <Stack> invalidInventory(
        session: SpearShieldSession<Stack>,
        observation: SpearShieldObservation,
        stopUse: Boolean,
    ): SpearShieldTransition<Stack>? {
        if (session.route is SpearShieldRoute.AlreadyEquipped) {
            return null
        }

        if (observation.inventoryLayout != SpearShieldInventoryLayout.CHANGED &&
            observation.inventoryLayout != SpearShieldInventoryLayout.NOT_REQUIRED &&
            observation.inventoryLayout != SpearShieldInventoryLayout.RESTORED_AFTER_BREAK) {
            return null
        }

        return abort(session, SpearShieldAbortReason.INVENTORY_CHANGED, stopUse)
    }

    private fun <Stack> restoreBrokenShield(
        session: SpearShieldSession<Stack>,
        stopUse: Boolean,
    ): SpearShieldTransition<Stack> {
        val snapshot = session.swapSnapshot()
            ?: return abort(session, SpearShieldAbortReason.SHIELD_BROKEN, stopUse)
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

    private fun <Stack> beginAcquisition(
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

    private fun <Stack> startBlocking(
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

    private fun <Stack> lower(
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

    private fun <Stack> beginRestore(
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

        if (layout != SpearShieldInventoryLayout.EQUIPPED &&
            layout != SpearShieldInventoryLayout.SHIELD_BROKEN) {
            return abort(session, SpearShieldAbortReason.INVENTORY_CHANGED, stopUse = false)
        }

        val snapshot = session.swapSnapshot()
            ?: return abort(session, SpearShieldAbortReason.INVALID_INVENTORY_LAYOUT, stopUse = false)

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

    private fun <Stack> abort(
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

    private fun <Stack> canRaiseAgain(
        session: SpearShieldSession<Stack>,
        current: SpearShieldState.LoweredAwaitingRestore<Stack>,
        observation: SpearShieldObservation,
    ): Boolean {
        if (!observation.threatPresent || !observation.aligned) {
            return false
        }

        if (current.restoreAtTick != null && observation.tick >= current.restoreAtTick) {
            return false
        }

        return session.route is SpearShieldRoute.AlreadyEquipped ||
            observation.inventoryLayout == SpearShieldInventoryLayout.EQUIPPED
    }

    private fun <Stack> reservationCommand(
        route: SpearShieldRoute<Stack>,
    ): List<SpearShieldCommand<Stack>> = if (route is SpearShieldRoute.SwapToOffhand) {
        listOf(SpearShieldCommand.ReserveOffhand)
    } else {
        emptyList()
    }

    private fun <Stack> SpearShieldSession<Stack>.rememberKey(useKeyDown: Boolean) =
        copy(previousUseKeyDown = useKeyDown)

    private fun <Stack> SpearShieldSession<Stack>.transferManualUseOnFreshPress(
        useKeyDown: Boolean,
    ): SpearShieldSession<Stack> = copy(
        useOwnership = if (!previousUseKeyDown && useKeyDown) {
            SpearShieldUseOwnership.MANUAL
        } else {
            useOwnership
        },
        previousUseKeyDown = useKeyDown,
    )

    private fun <Stack> SpearShieldSession<Stack>.swapSnapshot(): SpearShieldInventorySnapshot<Stack>? =
        (route as? SpearShieldRoute.SwapToOffhand)?.snapshot

    private fun <Stack> unchanged(state: SpearShieldState<Stack>) = SpearShieldTransition(state)
}
