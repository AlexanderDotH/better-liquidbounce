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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot

import kotlin.math.abs

internal enum class MaceUsePolicy {
    Off,
    HeldMace,
    HeldOrHotbar,
}

internal enum class MaceKillFightBotState {
    Unavailable,
    Ready,
    RouteActive,
    Rejected,
}

internal val MaceKillFightBotState.reservesKillAuraSubsystems: Boolean
    get() = this == MaceKillFightBotState.Ready || this == MaceKillFightBotState.RouteActive

internal val MaceKillFightBotState.retainsRejectedTarget: Boolean
    get() = this == MaceKillFightBotState.Rejected

internal sealed interface FightBotMaceUseSource {
    data object MainHand : FightBotMaceUseSource
    data class Hotbar(val slot: Int) : FightBotMaceUseSource
}

internal fun selectMaceUseSource(
    policy: MaceUsePolicy,
    mainHandMace: Boolean,
    selectedHotbarSlot: Int,
    hotbarMaceSlots: Iterable<Int>,
): FightBotMaceUseSource? {
    if (policy == MaceUsePolicy.Off) return null
    if (mainHandMace) return FightBotMaceUseSource.MainHand
    if (policy == MaceUsePolicy.HeldMace) return null

    return hotbarMaceSlots.minByOrNull { abs(it - selectedHotbarSlot) }
        ?.let(FightBotMaceUseSource::Hotbar)
}

internal enum class MaceKillFightBotTerminal {
    Completion,
    Rejection,
    TargetLoss,
    Disable,
    Death,
    Disconnect,
    WorldChange,
}

internal data class MaceKillFightBotCleanup(
    val terminal: MaceKillFightBotTerminal,
    val resetSilentSlot: Boolean,
)

internal fun fightBotMaceCleanup(
    terminal: MaceKillFightBotTerminal,
    source: FightBotMaceUseSource?,
) = MaceKillFightBotCleanup(
    terminal = terminal,
    resetSilentSlot = source is FightBotMaceUseSource.Hotbar,
)

internal object FightBotMaceUseRequester
