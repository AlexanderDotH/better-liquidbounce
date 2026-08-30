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
package net.ccbluex.liquidbounce.features.module.modules.combat.fightbot

import net.ccbluex.liquidbounce.common.Tagged
import kotlin.math.abs

internal enum class SpearKillFightBotState {
    Unavailable,
    Charging,
    RouteActive,
    Rejected,
}

internal val SpearKillFightBotState.reservesKillAuraSubsystems: Boolean
    get() = this == SpearKillFightBotState.Charging || this == SpearKillFightBotState.RouteActive

internal val SpearKillFightBotState.retainsRejectedTarget: Boolean
    get() = this == SpearKillFightBotState.Rejected

internal sealed interface FightBotSpearUseSource {
    data object MainHand : FightBotSpearUseSource
    data object Offhand : FightBotSpearUseSource
    data class Hotbar(val slot: Int) : FightBotSpearUseSource
}

internal fun selectFightBotSpearUseSource(
    automation: FightBotSpearAutomation,
    mainHandSpear: Boolean,
    offhandSpear: Boolean,
    selectedHotbarSlot: Int,
    hotbarSpearSlots: Iterable<Int>,
): FightBotSpearUseSource? {
    if (automation == FightBotSpearAutomation.Off) return null
    if (mainHandSpear) return FightBotSpearUseSource.MainHand
    if (offhandSpear) return FightBotSpearUseSource.Offhand
    if (automation == FightBotSpearAutomation.HeldSpear) return null

    return hotbarSpearSlots.minByOrNull { abs(it - selectedHotbarSlot) }
        ?.let(FightBotSpearUseSource::Hotbar)
}

internal enum class SpearKillFightBotTerminal {
    Completion,
    Rejection,
    TargetLoss,
    Disable,
    Death,
    Disconnect,
    WorldChange,
}

internal data class SpearKillFightBotCleanup(
    val terminal: SpearKillFightBotTerminal,
    val stopUse: Boolean,
    val resetSilentSlot: Boolean,
)

internal fun fightBotSpearCleanup(
    terminal: SpearKillFightBotTerminal,
    startedUse: Boolean,
    selectedSilentSlot: Boolean,
) = SpearKillFightBotCleanup(
    terminal = terminal,
    stopUse = startedUse,
    resetSilentSlot = selectedSilentSlot,
)

internal fun shouldStopFightBotSpearUse(
    startedUse: Boolean,
    isUsingItem: Boolean,
    isSameHand: Boolean,
    isUsingSpear: Boolean,
): Boolean = startedUse && isUsingItem && isSameHand && isUsingSpear
