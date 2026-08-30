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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research
import net.ccbluex.liquidbounce.common.attack.AcceptedAttackResult
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.blink.BlinkManager
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
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

internal fun MaceKillModuleState.recordResearchPacketDelivery(
    packet: ServerboundMovePlayerPacket,
    delivered: Boolean,
    queuedByBlink: Boolean,
) {
    val execution = researchExecution ?: return
    val context = researchPacketContexts.remove(packet) ?: return
    val delivery = when {
        queuedByBlink -> MaceClipResearchPacketDelivery.QUEUED
        delivered -> MaceClipResearchPacketDelivery.DELIVERED
        else -> MaceClipResearchPacketDelivery.CANCELLED
    }
    researchRuntime.recordPacket(
        execution.sessionId,
        context.phase,
        context.sequence,
        player.tickCount,
        context.position,
        packet.onGround,
        delivery,
    )
    if (context.phase == MaceClipResearchPhase.PRIME) {
        recordMaceKillPrimePacketResolution(execution, context)
        return
    }
    if (!delivered) return
    recordMaceKillMovementPacketResolution(execution, context)
}

private fun MaceKillModuleState.recordMaceKillPrimePacketResolution(
    execution: MaceKillResearchExecution,
    context: MaceKillResearchPacketContext,
) {
    execution.primingResolved++
    if (execution.primingResolved != execution.descriptor.primingPackets) return
    researchRuntime.recordPhaseCompleted(
        execution.sessionId,
        context.phase,
        player.tickCount,
        context.position,
    )
}

private fun MaceKillModuleState.recordMaceKillMovementPacketResolution(
    execution: MaceKillResearchExecution,
    context: MaceKillResearchPacketContext,
) {
    researchRuntime.recordPhaseCompleted(
        execution.sessionId,
        context.phase,
        player.tickCount,
        context.position,
    )
    if (context.outbound == true) execution.outboundDelivered++
    if (context.outbound == false) execution.returnDelivered++
}

internal fun MaceKillModuleState.currentResearchPhase(): MaceClipResearchPhase? = researchExecution?.let { execution ->
    when {
        routeSession.pendingOutboundStep -> execution.descriptor.phaseForMovement(
            outbound = true,
            index = execution.outboundDelivered,
        )
        routeSession.recovering -> execution.descriptor.phaseForMovement(
            outbound = false,
            index = execution.returnDelivered,
        )
        else -> MaceClipResearchPhase.PRIME
    }
}
