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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.clearAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.reservesKillAuraSubsystems
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.isKillAuraIntegrationAvailable
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.isSafeSpearKillCombatTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.isSpearKillTargetCandidateEligible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearFightBotSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.rejectFightBotSpearUse
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal fun SpearKillModuleState.prepareFacadeSetbackCorrection(
    packet: ClientboundPlayerPositionPacket,
    player: Player,
) = preparePacketSetback(packet, player)

internal fun SpearKillModuleState.finishFacadeSetbackCorrection(
    packet: ClientboundPlayerPositionPacket,
    player: Player,
) = finishPacketSetback(packet, player)

internal fun SpearKillModuleState.clearFacadeAttack(reason: String) = clearAttack(reason)

internal fun SpearKillModuleState.resolveFightBotState(target: LivingEntity): SpearKillFightBotState =
    fightBotSpearState.takeIf { fightBotSpearTarget === target } ?: SpearKillFightBotState.Unavailable

internal fun SpearKillModuleState.reservesFightBotUse(target: LivingEntity?): Boolean = target != null &&
    fightBotSpearTarget === target && fightBotSpearState.reservesKillAuraSubsystems

internal fun SpearKillModuleState.requestFightBotUse(target: LivingEntity): SpearKillFightBotState = when {
    isSpearKillTargetRejected(target) -> rejectFightBotSpearUse(target)
    !canPrepareFightBotSpearUse(target) -> unavailableFightBotSpearUse(target)
    else -> prepareFightBotSpearUse(target)
}

internal fun SpearKillModuleState.releaseFightBotUse(terminal: SpearKillFightBotTerminal) {
    if (fightBotSpearTarget != null && hasActiveAttackPath) {
        clearAttack("fightbot-${terminal.name.lowercase()}")
        return
    }
    clearFightBotSpearUse(terminal)
}

internal fun SpearKillModuleState.acceptsKillAuraTarget(target: LivingEntity): Boolean {
    val lockedTarget = lockedAStarTarget
    return isKillAuraIntegrationAvailable &&
        (lockedTarget == null || lockedTarget === target) &&
        isSpearKillTargetCandidateEligible(
            isCombatSafe = target.isSafeSpearKillCombatTarget(),
            isAlive = target.isAlive && !target.isRemoved,
            isInCurrentWorld = target.level() === world,
            isWithinRange = player.distanceTo(target) in 3f..maxTargetDistance,
            isRejected = isSpearKillTargetRejected(target),
        )
}
