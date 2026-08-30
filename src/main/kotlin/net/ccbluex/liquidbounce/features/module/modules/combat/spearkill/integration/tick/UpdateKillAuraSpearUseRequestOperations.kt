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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.SpearKillFightBotTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.KILL_AURA_DISABLED_REASON
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillInheritedUseAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.acceptsKillAuraDelegation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.activeSpearKillTargetLock
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasActiveAttackPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.hasSpearKillReturnWork
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.killAuraOwnsAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.ownsKillAuraRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.resolveSpearKillInheritedUseAction

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.abortSpearKillAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearFightBotSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearKillAuraSpearUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.releaseStandaloneRemoteMovementOwnership
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.resetAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.useItem
import net.ccbluex.liquidbounce.utils.item.isSpear
import net.ccbluex.liquidbounce.utils.network.useItem
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.updateKillAuraSpearUseRequest() {
    if (fightBotSpearTarget != null) {
        clearKillAuraSpearUse()
        return
    }

    val target = currentKillAuraSpearUseTarget()
    val precharge = target == null &&
        acceptsKillAuraDelegation &&
        shouldPrechargeDelegatedKillAura()
    if (target == null && !precharge) {
        clearKillAuraSpearUse()
        return
    }

    killAuraSpearTarget = target
    killAuraSpearPrechargeActive = precharge
    if (!maintainKillAuraSpearUse()) {
        clearKillAuraSpearUse()
    }
}

internal fun SpearKillModuleState.currentKillAuraSpearUseTarget(): LivingEntity? {
    val ownedTarget = activeSpearKillTargetLock(
        lockedTarget = lockedAStarTarget,
        routeActive = ownsKillAuraRoute && hasActiveAttackPath,
        routePreparationActive = packetRoutePreparationActive &&
            pendingKillAuraTarget === lockedAStarTarget,
    )
    if (ownedTarget != null) return ownedTarget
    if (!acceptsKillAuraDelegation) return null
    return delegatedKillAuraTarget()
}

internal fun SpearKillModuleState.maintainKillAuraSpearUse(): Boolean {
    stopDelegatedKillAuraBlocking(player.isUsingItem)

    return when (resolveSpearKillInheritedUseAction(
        requestActive = true,
        mainHandSpear = player.mainHandItem.isSpear,
        offhandSpear = player.offhandItem.isSpear,
        isUsingItem = player.isUsingItem,
        isUsingSpear = isUsingSpear,
    )) {
        SpearKillInheritedUseAction.NONE -> false
        SpearKillInheritedUseAction.KEEP_CURRENT_USE -> true
        SpearKillInheritedUseAction.START_MAIN_HAND -> startKillAuraSpearUse(InteractionHand.MAIN_HAND)
        SpearKillInheritedUseAction.START_OFF_HAND -> startKillAuraSpearUse(InteractionHand.OFF_HAND)
    }
}

internal fun SpearKillModuleState.startKillAuraSpearUse(hand: InteractionHand): Boolean {
    if (useItem(hand) !is InteractionResult.Success) return false

    killAuraStartedSpearUse = true
    killAuraSpearUseHand = hand
    return true
}

internal fun SpearKillModuleState.cancelKillAuraPreparation() {
    if (killAuraOwnsAttempt) abortSpearKillAttempt(KILL_AURA_DISABLED_REASON)
    movementAssistPreparationActive = false
    pendingKillAuraTarget = null
    clearAStarTargetLock()
}

internal fun SpearKillModuleState.beginKillAuraOwnedReturn() {
    killAuraReturnActive = true
    val motionReturnPrepared = prepareKillAuraOwnedMotionReturn()

    abortSpearKillAttempt(KILL_AURA_DISABLED_REASON)
    clearKillAuraSpearUse()
    clearFightBotSpearUse(SpearKillFightBotTerminal.TargetLoss)
    manualAttackRequestLatched = false
    movementAssistPreparationActive = false
    pendingKillAuraTarget = null
    previewTarget = null

    if (packetBootSession.active) {
        resetAttack()
    } else if (!motionReturnPrepared) {
        attackMovements.clear()
        player.deltaMovement = Vec3.ZERO
        releaseStandaloneRemoteMovementOwnership()
    }

    packetAStarAttackActive = false
    clearAStarRenderPath()
    clearAStarTargetLock()
    fallDamageDeliveryTracker.clear()
    synchronizeSpearKillServerSneak()
    killAuraReturnActive = hasSpearKillReturnWork
}
