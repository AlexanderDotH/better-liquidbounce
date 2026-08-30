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

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.armMaceKillHoldAttackRetry
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.maceKillInstantTerminalDecision
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.fightbot.MaceKillFightBotState
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.reach.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research.*
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

internal fun MaceKillModuleState.handleInstantSessionOutcome(
    outcome: MaceClipReachSessionOutcome,
    abortRoute: Boolean = true,
) {
    val session = activeClipReachSession ?: return
    val decision = maceKillInstantTerminalDecision(outcome, session.strikeCommitted)
    if (!decision.rejectAttempt || instantTerminalHandled) return

    instantTerminalHandled = true
    activeRouteTarget?.let { rejectedTargets.reject(it, player.tickCount) }
    routeRejected = true
    holdAttackState = armMaceKillHoldAttackRetry(holdAttackState)
    instantRouteBackoff.reject(player.tickCount)
    decision.notificationKey?.let(::notifyMaceFailure)
    if (activeRouteOwner == MaceKillRouteOwner.FIGHT_BOT) {
        fightBotMaceState = MaceKillFightBotState.Rejected
    }
    debugMaceKill("instant-terminal") {
        listOf("outcome" to outcome, "strike-committed" to session.strikeCommitted)
    }
    if (abortRoute && decision.abortRoute && routeEngine.ownsMovement) beginSafeRouteAbort()
}

internal fun MaceKillModuleState.sendMaceKillPrimingPackets(position: Vec3, count: Int): Boolean {
    primingDeliveryFailed = false
    repeat(count) {
        val packet = createMaceKillMovementPacket(
            position,
            maceKillRoutePacketGrounded(position, identityOwnedByRoute = true),
        )
        researchExecution?.let { execution ->
            researchPacketContexts[packet] = MaceKillResearchPacketContext(
                sequence = execution.nextPacketSequence++,
                phase = MaceClipResearchPhase.PRIME,
                position = position,
                outbound = null,
            )
            researchRuntime.recordPhaseStarted(
                execution.sessionId,
                MaceClipResearchPhase.PRIME,
                player.tickCount,
                position,
            )
        }
        primingPackets += packet
        network.send(packet)
        if (packet in primingPackets) {
            confirmPrimingPacket(packet, cancelled = true)
        }
    }
    return !primingDeliveryFailed
}

internal fun MaceKillModuleState.confirmPrimingPacket(packet: ServerboundMovePlayerPacket, cancelled: Boolean) {
    primingPackets -= packet
    val queuedByBlink = BlinkManager.packetQueue.any { it.packet === packet }
    if (queuedByBlink) BlinkManager.packetQueue.removeIf { it.packet === packet }
    val delivered = !cancelled && !queuedByBlink
    recordResearchPacketDelivery(packet, delivered, queuedByBlink)
    if (!delivered) primingDeliveryFailed = true
}
