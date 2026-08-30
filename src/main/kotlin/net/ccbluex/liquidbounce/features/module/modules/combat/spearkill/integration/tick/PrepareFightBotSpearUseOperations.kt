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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity

import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.FightBotSpearAutomation
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.FightBotSpearUseSource
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.selectFightBotSpearUseSource
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.FightBotSpearUseRequester
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.acceptsKillAuraDelegation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.isSpearKillTargetCandidateEligible
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearFightBotSpearUse
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.item.isSpear

internal fun SpearKillModuleState.prepareFightBotSpearUse(target: LivingEntity): SpearKillFightBotState {
    if (fightBotSpearTarget !== target) {
        clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
    }
    fightBotSpearTarget = target

    return when {
        hasActiveAttackPath -> setFightBotSpearState(SpearKillFightBotState.RouteActive)
        !refreshFightBotSilentSlot() -> failFightBotSpearUse()
        isUsingSpear -> setFightBotSpearState(SpearKillFightBotState.Charging)
        else -> startFightBotSpearUse()
    }
}

internal fun SpearKillModuleState.refreshFightBotSilentSlot(): Boolean = fightBotSilentHotbarSlot?.let { slot ->
    SilentHotbar.selectSlotSilently(FightBotSpearUseRequester, slot, 2)
} ?: true

internal fun SpearKillModuleState.startFightBotSpearUse(): SpearKillFightBotState {
    stopDelegatedKillAuraBlocking(player.isUsingItem)

    return if (player.isUsingItem) failFightBotSpearUse() else resolveAndStartFightBotSpearUse()
}

internal fun SpearKillModuleState.resolveAndStartFightBotSpearUse(): SpearKillFightBotState {
    val source = resolveFightBotSpearUseSource() ?: return failFightBotSpearUse()
    val hand = resolveFightBotSpearHand(source) ?: return failFightBotSpearUse()
    return if (useItem(hand) is InteractionResult.Success) {
        fightBotStartedUse = true
        fightBotUseHand = hand
        setFightBotSpearState(SpearKillFightBotState.Charging)
    } else {
        failFightBotSpearUse()
    }
}

internal fun SpearKillModuleState.resolveFightBotSpearHand(source: FightBotSpearUseSource): InteractionHand? = when (source) {
    FightBotSpearUseSource.MainHand -> InteractionHand.MAIN_HAND
    FightBotSpearUseSource.Offhand -> InteractionHand.OFF_HAND
    is FightBotSpearUseSource.Hotbar -> {
        if (!SilentHotbar.selectSlotSilently(FightBotSpearUseRequester, source.slot, 2)) {
            null
        } else {
            fightBotSilentHotbarSlot = source.slot
            InteractionHand.MAIN_HAND
        }
    }
}

internal fun SpearKillModuleState.unavailableFightBotSpearUse(target: LivingEntity): SpearKillFightBotState {
    if (fightBotSpearTarget === target) {
        clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
    }
    return SpearKillFightBotState.Unavailable
}

internal fun SpearKillModuleState.failFightBotSpearUse(): SpearKillFightBotState {
    clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
    return SpearKillFightBotState.Unavailable
}

internal fun SpearKillModuleState.setFightBotSpearState(state: SpearKillFightBotState): SpearKillFightBotState {
    fightBotSpearState = state
    return state
}

internal fun SpearKillModuleState.canPrepareFightBotSpearUse(target: LivingEntity): Boolean {
    val lockedTarget = lockedAStarTarget
    return enabled && running && acceptsKillAuraDelegation &&
        fightBotSpearAutomation != FightBotSpearAutomation.Off &&
        (lockedTarget == null || lockedTarget === target) &&
        (!hasActiveAttackPath || fightBotSpearTarget === target) &&
        !packetBootSession.recovering && !setbackRollback.confirming && !packetSetbackRecoveryAttempted &&
        isSpearKillTargetCandidateEligible(
            isCombatSafe = target.shouldBeAttacked(),
            isAlive = target.isAlive && !target.isRemoved,
            isInCurrentWorld = target.level() === world,
            isWithinRange = player.distanceTo(target) in 3f..maxTargetDistance,
            isRejected = false,
        )
}

internal fun SpearKillModuleState.resolveFightBotSpearUseSource(): FightBotSpearUseSource? = selectFightBotSpearUseSource(
    automation = fightBotSpearAutomation,
    mainHandSpear = player.mainHandItem.isSpear,
    offhandSpear = player.offhandItem.isSpear,
    selectedHotbarSlot = SilentHotbar.serversideSlot,
    hotbarSpearSlots = Slots.Hotbar.asSequence()
        .filter { it.itemStack.isSpear }
        .mapNotNull { it.hotbarIndex }
        .toList(),
)
