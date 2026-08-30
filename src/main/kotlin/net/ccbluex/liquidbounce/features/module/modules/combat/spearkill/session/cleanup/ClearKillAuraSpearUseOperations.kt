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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.shouldStopSpearKillInheritedUse
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.item.isSpear

internal fun SpearKillModuleState.clearKillAuraSpearUse() {
    val currentPlayer = mc.player
    if (currentPlayer != null && shouldStopSpearKillInheritedUse(
            startedUse = killAuraStartedSpearUse,
            isUsingItem = currentPlayer.isUsingItem,
            isSameHand = killAuraSpearUseHand == currentPlayer.usedItemHand,
            isUsingSpear = currentPlayer.useItem.isSpear,
        )
    ) {
        mc.gameMode?.releaseUsingItem(currentPlayer)
    }

    killAuraSpearTarget = null
    killAuraSpearPrechargeActive = false
    killAuraStartedSpearUse = false
    killAuraSpearUseHand = null
}
