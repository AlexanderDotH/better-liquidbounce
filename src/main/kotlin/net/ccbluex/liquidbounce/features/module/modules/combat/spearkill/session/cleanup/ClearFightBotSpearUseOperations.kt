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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.fightBotSpearCleanup
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.shouldStopFightBotSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.FightBotSpearUseRequester
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.item.isSpear

internal fun SpearKillModuleState.clearFightBotSpearUse(terminal: SpearKillFightBotTerminal) {
    val cleanup = fightBotSpearCleanup(
        terminal = terminal,
        startedUse = fightBotStartedUse,
        selectedSilentSlot = fightBotSilentHotbarSlot != null,
    )
    val currentPlayer = mc.player
    if (currentPlayer != null && shouldStopFightBotSpearUse(
            startedUse = cleanup.stopUse,
            isUsingItem = currentPlayer.isUsingItem,
            isSameHand = fightBotUseHand == null || currentPlayer.usedItemHand == fightBotUseHand,
            isUsingSpear = currentPlayer.useItem.isSpear,
        )
    ) {
        mc.gameMode?.releaseUsingItem(currentPlayer)
    }
    if (cleanup.resetSilentSlot) {
        SilentHotbar.resetSlot(FightBotSpearUseRequester)
    }

    fightBotSpearTarget = null
    fightBotSpearState = SpearKillFightBotState.Unavailable
    fightBotStartedUse = false
    fightBotSilentHotbarSlot = null
    fightBotUseHand = null
}
