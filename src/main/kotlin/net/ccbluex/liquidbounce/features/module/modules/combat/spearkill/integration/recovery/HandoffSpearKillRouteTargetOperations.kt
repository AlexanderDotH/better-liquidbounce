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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketTargetState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activePacketRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.classifySpearKillPacketTargetState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.isSafeSpearKillCombatTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.shouldKeepSpearKillTerminalPending
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.shouldTrackSpearKillPacketTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.usesPacketMovementMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.shouldReplanSpearKillAStarTarget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.terminatePacketFollow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.canReplaceRemainingApproach
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.canReplaceRemainingOutbound
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.SpearKillActiveTargetFollowDecision
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.resolveSpearKillActiveTargetFollow
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.handoffSpearKillRouteTarget(previousTarget: LivingEntity, nextTarget: LivingEntity) {
    debugSpearKill("TARGET_CHAIN") {
        listOf(
            "tick" to player.tickCount,
            "committed_offset" to spearKillDebugVector(packetBootSession.committedOffset),
        ) + spearKillDebugTargetFields(previousTarget, prefix = "previous") +
            spearKillDebugTargetFields(nextTarget, prefix = "next")
    }
    if (fightBotSpearTarget === previousTarget) fightBotSpearTarget = nextTarget
    if (killAuraSpearTarget === previousTarget) killAuraSpearTarget = nextTarget
    if (pendingKillAuraTarget === previousTarget) pendingKillAuraTarget = nextTarget
    lockedAStarTarget = nextTarget
    previewTarget = nextTarget
}

internal fun SpearKillModuleState.followLockedPacketTarget() {
    if (!usesPacketMovementMode || !packetBootSession.active) return

    val target = lockedAStarTarget ?: return
    if (target.isRemoved) {
        attemptTracker.markTargetRemoved()
    }
    if (terminateInactiveLockedPacketTarget(target)) return

    when (resolveSpearKillActiveTargetFollow(
        isCombatSafe = target.isSafeSpearKillCombatTarget(),
        isRecovering = { packetBootSession.recovering },
        tracksPacketTarget = { shouldTrackSpearKillPacketTarget(activePacketRoutingMode) },
    )) {
        SpearKillActiveTargetFollowDecision.TERMINATE_UNREACHABLE -> {
            terminatePacketFollow(target, PacketFollowTermination.UNREACHABLE)
            return
        }
        SpearKillActiveTargetFollowDecision.PAUSE -> return
        SpearKillActiveTargetFollowDecision.CONTINUE -> Unit
    }
    if (handlePendingTerminalCommit(target)) return
    replanTrackedPacketTarget(target)
}

private fun SpearKillModuleState.terminateInactiveLockedPacketTarget(target: LivingEntity): Boolean {
    val state = classifySpearKillPacketTargetState(
        isAlive = target.isAlive,
        isRemoved = target.isRemoved,
        isInCurrentWorld = target.level() === world,
        isWithinRange = (packetSessionOrigin?.add(packetBootSession.committedOffset)
            ?: player.position()).distanceTo(target.position()) <= maxTargetDistance,
    )
    val termination = when (state) {
        SpearKillPacketTargetState.ACTIVE -> return false
        SpearKillPacketTargetState.DEFEATED -> PacketFollowTermination.DEFEATED
        SpearKillPacketTargetState.UNREACHABLE -> PacketFollowTermination.UNREACHABLE
    }
    terminatePacketFollow(target, termination)
    return true
}

private fun SpearKillModuleState.replanTrackedPacketTarget(target: LivingEntity) {
    val plannedPosition = plannedAStarTargetPosition ?: return
    val canReplacePath = if (packetAStarAttackActive) {
        packetBootSession.canReplaceRemainingApproach
    } else {
        packetBootSession.canReplaceRemainingOutbound
    }
    if (!shouldReplanSpearKillAStarTarget(
            plannedPosition,
            target.position(),
            player.tickCount - aStarPlanTick,
            plannedAStarTargetVelocity,
        ) || !canReplacePath || plannedPacket != null ||
        awaitingVanillaMovementPacket
    ) {
        return
    }

    val sessionOrigin = packetSessionOrigin ?: return
    val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
    if (packetAStarAttackActive) {
        val result = replanLockedAStarTarget(target, routeOrigin, sessionOrigin)
        if (!shouldKeepSpearKillTerminalPending(result)) {
            terminatePacketFollow(target, PacketFollowTermination.BLOCKED)
        }
    } else {
        replanLockedDirectPacketTarget(target, routeOrigin, sessionOrigin)
    }
}
