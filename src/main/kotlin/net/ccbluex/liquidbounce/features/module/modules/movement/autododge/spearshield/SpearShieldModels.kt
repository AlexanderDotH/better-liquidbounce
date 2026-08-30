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

enum class SpearShieldHand {
    MAIN_HAND,
    OFF_HAND,
}

enum class SpearShieldInventoryLayout {
    NOT_REQUIRED,
    ORIGINAL,
    EQUIPPED,
    SHIELD_BROKEN,
    RESTORED_AFTER_BREAK,
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

    inline fun classify(
        containerId: Int,
        sourceStack: @UnsafeVariance Stack,
        offhandStack: @UnsafeVariance Stack,
        stacksMatch: (Stack, Stack) -> Boolean,
        isEmpty: (Stack) -> Boolean,
        expectBrokenShieldRestored: Boolean = false,
    ): SpearShieldInventoryLayout {
        if (this.containerId != containerId) return SpearShieldInventoryLayout.CHANGED
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
    data class SwapToOffhand<Stack>(val snapshot: SpearShieldInventorySnapshot<Stack>) : SpearShieldRoute<Stack>
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
    val route: SpearShieldRoute<Stack>?,
    val usingItem: Boolean,
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
    data class SwapIntoOffhand<Stack>(val snapshot: SpearShieldInventorySnapshot<Stack>) : SpearShieldCommand<Stack>
    data class StartShieldUse(val hand: SpearShieldHand) : SpearShieldCommand<Nothing>
    data object StopShieldUse : SpearShieldCommand<Nothing>
    data class RestoreOffhand<Stack>(val snapshot: SpearShieldInventorySnapshot<Stack>) : SpearShieldCommand<Stack>
    data object ReleaseOffhandReservation : SpearShieldCommand<Nothing>
}

data class SpearShieldTransition<out Stack>(
    val state: SpearShieldState<Stack>,
    val commands: List<SpearShieldCommand<Stack>> = emptyList(),
)
