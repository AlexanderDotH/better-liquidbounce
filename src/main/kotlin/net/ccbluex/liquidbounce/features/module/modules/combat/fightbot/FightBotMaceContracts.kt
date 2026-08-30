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

import kotlin.math.abs

internal enum class FightBotMaceState {
    Unavailable,
    Ready,
    RouteActive,
    Rejected,
}

internal val FightBotMaceState.reservesKillAuraSubsystems: Boolean
    get() = this == FightBotMaceState.Ready || this == FightBotMaceState.RouteActive

internal val FightBotMaceState.retainsRejectedTarget: Boolean
    get() = this == FightBotMaceState.Rejected

internal sealed interface FightBotMaceUseSource {
    data object MainHand : FightBotMaceUseSource
    data class Hotbar(val slot: Int) : FightBotMaceUseSource
}

internal fun selectFightBotMaceUseSource(
    automation: FightBotMaceAutomation,
    mainHandMace: Boolean,
    selectedHotbarSlot: Int,
    hotbarMaceSlots: Iterable<Int>,
): FightBotMaceUseSource? {
    if (automation == FightBotMaceAutomation.Off) return null
    if (mainHandMace) return FightBotMaceUseSource.MainHand
    if (automation == FightBotMaceAutomation.HeldMace) return null

    return hotbarMaceSlots.minByOrNull { abs(it - selectedHotbarSlot) }
        ?.let(FightBotMaceUseSource::Hotbar)
}

internal enum class FightBotMaceTerminal {
    Completion,
    Rejection,
    TargetLoss,
    Disable,
    Death,
    Disconnect,
    WorldChange,
}

internal enum class FightBotRemoteWeapon {
    Mace,
    Spear,
}

internal fun <T> selectFightBotRouteTarget(maceRouteTarget: T?, spearRouteTarget: T?): T? =
    maceRouteTarget ?: spearRouteTarget

/** Retains an owned route, then prefers held weapons, with Mace winning only the hotbar tie. */
internal fun selectFightBotRemoteWeapon(
    maceSource: FightBotMaceUseSource?,
    spearSource: FightBotSpearUseSource?,
    maceRouteActive: Boolean = false,
    spearRouteActive: Boolean = false,
): FightBotRemoteWeapon? = when {
    maceRouteActive -> FightBotRemoteWeapon.Mace
    spearRouteActive -> FightBotRemoteWeapon.Spear
    maceSource == FightBotMaceUseSource.MainHand -> FightBotRemoteWeapon.Mace
    spearSource == FightBotSpearUseSource.MainHand || spearSource == FightBotSpearUseSource.Offhand ->
        FightBotRemoteWeapon.Spear
    maceSource is FightBotMaceUseSource.Hotbar -> FightBotRemoteWeapon.Mace
    spearSource is FightBotSpearUseSource.Hotbar -> FightBotRemoteWeapon.Spear
    else -> null
}

/** Keeps the previously shared Mace test source-compatible while each package owns its source type. */
internal fun selectFightBotRemoteWeapon(
    maceSource: Any,
    spearSource: FightBotSpearUseSource?,
    maceRouteActive: Boolean = false,
    spearRouteActive: Boolean = false,
): FightBotRemoteWeapon? = selectFightBotRemoteWeapon(
    maceSource = when (maceSource.javaClass.simpleName) {
        "MainHand" -> FightBotMaceUseSource.MainHand
        "Hotbar" -> FightBotMaceUseSource.Hotbar(slot = 0)
        else -> null
    },
    spearSource = spearSource,
    maceRouteActive = maceRouteActive,
    spearRouteActive = spearRouteActive,
)
