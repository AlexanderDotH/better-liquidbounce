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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.abortSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearFightBotSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearKillAuraSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearVirtualAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetSpearKillSpeedSession
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState

internal fun SpearKillModuleState.clearAttack(
    reason: String = "cleared",
    finishFallSafety: Boolean = true,
    allowFallSafetyPacket: Boolean = true,
) {
    killAuraReturnActive = false
    abortSpearKillAttempt(reason)
    clearKillAuraSpearUse()
    clearVirtualAttack(finishFallSafety, allowFallSafetyPacket)
    setbackGuard.clear()
    setbackRollback.clear()
    packetSetbackRecoveryAttempted = false
    pendingSetbackFallDistance = null
    pendingSetbackConfirmedOffset = null
    returnRecoveryTracker.clear()
    manualAttackRequestLatched = false
    clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
    resetSpearKillSpeedSession()
    ownedMovementPacketsThisTick = 0
    lastServerCorrectionTick = null
    synchronizeSpearKillServerSneak()
}
