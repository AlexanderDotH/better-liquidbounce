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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.orchestration

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.requiredMaceKillLocalRestore
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.features.input.InputTracker.isPressedOnAny
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.math.allEmpty
import net.ccbluex.liquidbounce.utils.raytracing.hasLineOfSight
import net.minecraft.core.*
import net.minecraft.network.protocol.game.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.*
import net.minecraft.world.entity.player.*
import net.minecraft.world.item.*
import net.minecraft.world.phys.*

internal fun MaceKillModuleState.applyMotionRoutePosition() {
    if (!motionRouteActive) return
    val origin = routeOrigin ?: return
    player.setPos(origin.add(routeSession.committedOffset))
}

internal fun MaceKillModuleState.maintainPacketRouteOrigin() {
    val origin = localPacketRouteOrigin ?: return
    val preservePhysicalMovement = researchExecution == null
    requiredMaceKillLocalRestore(
        packetRouteOwned = routeEngine.ownsMovement,
        preservePhysicalMovement = preservePhysicalMovement,
        origin = origin,
        currentPosition = player.position(),
    )?.let(player::setPos)
    if (!preservePhysicalMovement) player.deltaMovement = Vec3.ZERO
}

internal fun MaceKillModuleState.finishCompletedRouteSession() {
    check(routeEngine.ownsMovement && !routeSession.active && !routeEngine.awaitingStrike)
    maintainPacketRouteOrigin()
    activeClipReachSession?.complete()
    if (researchExecution != null || motionRouteActive) {
        if (!finishMaceKillFallSafety()) return
        routeEngine.releaseCompletedOwnership()
        finishInactiveRouteOwnership()
        return
    }

    returnConfirmation.onExactReturnDelivered(
        player.tickCount,
        maceKillReturnConfirmationTicks(activeRouteConfiguration?.routingMode),
    )
    if (!returnConfirmation.shouldRelease(player.tickCount)) return
    if (!finishMaceKillFallSafety()) return
    routeEngine.releaseCompletedOwnership()
    finishInactiveRouteOwnership()
}

internal fun MaceKillModuleState.maintainFightBotMaceLease() {
    if (activeRouteOwner != MaceKillRouteOwner.FIGHT_BOT) return
    if (pendingFightBotTerminal != null) return
    val target = activeRouteTarget
    val source = fightBotMaceSource
    val valid = target != null && fightBotMaceTarget === target && target.isAlive && !target.isRemoved &&
        integration.fightBotMacePolicy != MaceUsePolicy.Off && when (source) {
            FightBotMaceUseSource.MainHand -> player.mainHandItem.item == Items.MACE
            is FightBotMaceUseSource.Hotbar -> isMaceInHotbarSlot(source.slot) &&
                SilentHotbar.selectSlotSilently(FightBotMaceUseRequester, source.slot, 2)
            null -> false
        }
    if (valid) return

    fightBotMaceState = MaceKillFightBotState.Rejected
    beginFightBotTerminal(MaceKillFightBotTerminal.Rejection)
}

internal fun MaceKillModuleState.finishInactiveRouteOwnership() {
    if (routeEngine.ownsMovement) return

    val research = researchExecution
    if (research != null) {
        finishResearchProbeWhenReady(research)
        return
    }
    if (activeRouteOwner == MaceKillRouteOwner.NONE) return

    val completedOwner = activeRouteOwner
    finishMaceKillFallSafety()
    val rejected = routeRejected
    val effectiveFightBotTerminal = pendingFightBotTerminal
        ?: if (rejected) MaceKillFightBotTerminal.Rejection else MaceKillFightBotTerminal.Completion
    clearRouteOwnership(rejected)
    if (completedOwner == MaceKillRouteOwner.FIGHT_BOT) {
        if (effectiveFightBotTerminal == MaceKillFightBotTerminal.Rejection) {
            finalizeFightBotRejection()
        } else {
            clearFightBotMaceUse(effectiveFightBotTerminal)
        }
    }
}

internal fun MaceKillModuleState.clearRouteOwnership(rejected: Boolean = false) {
    val instantFailureHandled = instantTerminalHandled
    activeRouteTarget = null
    activeRouteOwner = MaceKillRouteOwner.NONE
    remoteStrikeEndpoint = null
    remoteStrikeTarget = null
    remoteStrikeFallResetPlan = null
    remoteStrikeEarliestTick = 0
    routeOrigin = null
    routeOriginBoundingBox = null
    routeRenderPath = emptyList()
    routeStepWaitTicks = 0
    routeStallTicks = 0
    routeResumeTick = 0
    plannedRoutePacket = null
    groundingPacketTracker.clear()
    primingPackets.clear()
    researchPacketContexts.clear()
    motionRouteActive = false
    activeVanillaVClipSegments = emptySet()
    activeClipReachSession = null
    instantRecoveryPlan = null
    instantCorrectionRecoveryActive = false
    lastInstantPlanBlockReason = null
    plannedTargetPosition = null
    routeChainCount = 0
    activeRouteConfiguration = null
    routeDeadlineTick = 0
    localPacketRouteOrigin = null
    returnConfirmation.clear()
    speedController.reset()
    fallSafetyLifecycle.invalidate()
    if (rejected && !instantFailureHandled) notifyMaceFailure("routeRejected")
    instantTerminalHandled = false
    routeRejected = false
}

internal fun MaceKillModuleState.finalizeFightBotRejection() {
    if (fightBotMaceSource is FightBotMaceUseSource.Hotbar) {
        SilentHotbar.resetSlot(FightBotMaceUseRequester)
    }
    fightBotMaceSource = null
    pendingFightBotTerminal = null
    fightBotMaceState = MaceKillFightBotState.Rejected
}
