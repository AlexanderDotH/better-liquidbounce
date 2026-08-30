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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketChainStartResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.spearKillDebugTargetFields
import net.ccbluex.liquidbounce.features.chat.notification
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.terminatePacketFollow(target: LivingEntity?, termination: PacketFollowTermination) {
    debugSpearKill("ROUTE_TERMINATE") {
        listOf(
            "tick" to player.tickCount,
            "termination" to termination,
            "reject_target" to termination.rejectTarget,
        ) + spearKillDebugTargetFields(target) + spearKillDebugSessionFields()
    }
    when (termination) {
        PacketFollowTermination.DEFEATED -> attemptTracker.markDefeated()
        PacketFollowTermination.BLOCKED -> attemptTracker.markBlocked()
        PacketFollowTermination.UNREACHABLE -> Unit
    }
    if (termination.rejectTarget && target != null) {
        rejectSpearKillTarget(target)
    } else if (target != null) {
        rejectedTargets.allow(target)
    }
    if (termination == PacketFollowTermination.DEFEATED && target != null) {
        when (tryStartPacketChainEffect(target)) {
            PacketChainStartResult.STARTED -> return
            PacketChainStartResult.FAILED -> Unit
        }
    }
    plannedAStarApproach = null
    plannedAStarTargetPosition = null
    plannedAStarTargetVelocity = Vec3.ZERO
    clearAStarRenderPath()
    beginSafeExactReturn()
    applyConfirmedPhysicalReturnPosition()
    synchronizeSpearKillServerSneak()
    val notificationKey = termination.notificationKey ?: return
    if (!failureNotificationGate.shouldNotify(player.tickCount)) return
    notification(
        name,
        message(notificationKey),
        NotificationEvent.Severity.ERROR,
    )
}
