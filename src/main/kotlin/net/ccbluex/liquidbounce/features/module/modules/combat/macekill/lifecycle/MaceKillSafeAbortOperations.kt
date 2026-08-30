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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle
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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.*
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

internal fun MaceKillModuleState.beginSafeRouteAbort() {
    if (!routeEngine.ownsMovement) return
    if (!routeSession.recovering) {
        if (tryBeginMaceKillSafeRecovery()) return
    }
    routeEngine.abort()
}

private fun MaceKillModuleState.tryBeginMaceKillSafeRecovery(): Boolean {
    val origin = routeOrigin
    val committedOffset = routeSession.committedOffset
    val exactRecovery = routeSession.exactRecoveryMovementsFrom(committedOffset)
    val recovery = exactRecovery?.let {
        if (activeClipReachSession == null) it else maceKillSafeClipRecoveryMovements(it)
    }
    if (origin == null || recovery == null) {
        fallSafetyLifecycle.invalidate()
        return false
    }
    if (!replanMaceKillFallSafety(
            origin.add(committedOffset), recovery, 0, activeMaceKillGroundPolicy(),
        )
    ) {
        rejectUnsafeMaceKillRecovery()
        return true
    }
    if (activeClipReachSession == null) return false
    routeEngine.beginPacketExactRecoveryFrom(committedOffset, recovery, routeStepWaitTicks)
    return true
}

private fun MaceKillModuleState.rejectUnsafeMaceKillRecovery() {
    routeRejected = true
    routeEngine.clear()
    finishInactiveRouteOwnership()
}

internal fun MaceKillModuleState.finishMaceKillFallSafety(): Boolean {
    val origin = routeOrigin
    return when (decideMaceKillFallSafetyFinish(
        lifecycle = fallSafetyLifecycle,
        finalPositionKnown = origin != null,
        connectionOpen = mc.connection?.connection?.isConnected == true,
        nearGround = origin?.let(::isMaceKillPositionNearGround) == true,
    )) {
        MaceKillFallSafetyFinishDecision.COMPLETE -> true
        MaceKillFallSafetyFinishDecision.WAIT_FOR_ROUTE_DELIVERY -> false
        MaceKillFallSafetyFinishDecision.RESET_LOCAL_FALL_DISTANCE -> {
            player.resetFallDistance()
            true
        }
        MaceKillFallSafetyFinishDecision.SEND_GROUNDING -> {
            if (origin == null) {
                fallSafetyLifecycle.confirmGrounding(delivered = false)
            } else {
                sendMaceKillGroundingPacket(origin)
            }
            false
        }
    }
}

internal fun MaceKillModuleState.sendMaceKillGroundingPacket(position: Vec3): Boolean {
    if (!isMaceKillPositionNearGround(position)) {
        fallSafetyLifecycle.confirmGrounding(delivered = false)
        return false
    }
    val packet = ServerboundMovePlayerPacket.Pos(
        position.x,
        position.y,
        position.z,
        true,
        player.horizontalCollision,
    )
    groundingPacketTracker.protect(packet)
    network.send(packet)
    if (groundingPacketTracker.discard(packet)) {
        fallSafetyLifecycle.confirmGrounding(delivered = false)
        return false
    }
    return true
}

internal fun MaceKillModuleState.networkSetbackBackoffTicks(): Int = activeRouteConfiguration?.timing?.setbackBackoffTicks ?: 0
