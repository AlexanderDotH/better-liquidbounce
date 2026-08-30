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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketRouteReplanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillTerminalChargeAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.resolveSpearKillTerminalChargeAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.shouldKeepSpearKillTerminalPending
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.shouldReplanSpearKillDirectTerminal
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.refreshSpearKillServerUse
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.terminatePacketFollow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.authorizeTerminalCommit
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.terminalAimLockComplete
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.commitOrReplanAStarTerminal(target: LivingEntity) {
    if (!packetBootSession.terminalAimLockComplete ||
        plannedPacket != null ||
        awaitingVanillaMovementPacket
    ) {
        return
    }
    if (!ensureSpearKillTerminalCharge(target)) return
    if (hasSafeLiveAStarTerminalCommit(target)) {
        if (!packetBootSession.authorizeTerminalCommit()) {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
        } else {
            attemptTracker.authorizeTerminal(player.tickCount)
        }
        return
    }

    val sessionOrigin = packetSessionOrigin ?: run {
        terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
        return
    }
    val replanResult = replanLockedAStarTarget(
        target = target,
        routeOrigin = sessionOrigin.add(packetBootSession.committedOffset),
        sessionOrigin = sessionOrigin,
    )
    if (!shouldKeepSpearKillTerminalPending(replanResult)) {
        terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
    }
}

internal fun SpearKillModuleState.commitOrReplanDirectTerminal(target: LivingEntity) {
    if (!packetBootSession.terminalAimLockComplete ||
        plannedPacket != null ||
        awaitingVanillaMovementPacket
    ) {
        return
    }
    if (!ensureSpearKillTerminalCharge(target)) return

    val plannedPosition = plannedAStarTargetPosition
    if (plannedPosition == null) {
        terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
        return
    }
    if (!shouldReplanDirectTerminal(target, plannedPosition)) {
        if (!packetBootSession.authorizeTerminalCommit()) {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
        } else {
            attemptTracker.authorizeTerminal(player.tickCount)
        }
        return
    }

    val sessionOrigin = packetSessionOrigin
    if (sessionOrigin == null) {
        terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
        return
    }
    when (installReplannedDirectPacketRoute(
        target = target,
        routeOrigin = sessionOrigin.add(packetBootSession.committedOffset),
        sessionOrigin = sessionOrigin,
    )) {
        SpearKillPacketRouteReplanResult.INSTALLED -> directTerminalReplanInstalled = true
        SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE -> Unit
        SpearKillPacketRouteReplanResult.BLOCKED ->
            terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
    }
}

private fun SpearKillModuleState.shouldReplanDirectTerminal(
    target: LivingEntity,
    plannedPosition: Vec3,
): Boolean = shouldReplanSpearKillDirectTerminal(
    plannedPosition = plannedPosition,
    currentPosition = target.position(),
    ticksSincePlan = player.tickCount - aStarPlanTick,
    plannedVelocity = plannedAStarTargetVelocity,
    terminalReplanInstalled = directTerminalReplanInstalled,
)

internal fun SpearKillModuleState.ensureSpearKillTerminalCharge(target: LivingEntity): Boolean {
    val schedule = remainingSpearKillTerminalSchedule()
    val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON)
    val action = if (schedule == null || kineticWeapon == null) {
        SpearKillTerminalChargeAction.INVALID
    } else {
        resolveSpearKillTerminalChargeAction(
            isUsingSpear = isUsingSpear,
            ticksUsingItem = player.ticksUsingItem,
            delayTicks = kineticWeapon.delayTicks,
            damageUseDuration = kineticWeapon.computeDamageUseDuration(),
            remainingHitTicks = schedule.hitTick,
        )
    }

    return when (action) {
        SpearKillTerminalChargeAction.READY -> true
        SpearKillTerminalChargeAction.WAIT -> false
        SpearKillTerminalChargeAction.REFRESH -> {
            refreshSpearKillServerUse()
            false
        }
        SpearKillTerminalChargeAction.INVALID -> {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            false
        }
    }
}
