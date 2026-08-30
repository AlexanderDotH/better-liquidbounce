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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.SpearKillPathSchedule
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.buildSpearKillTerminalSchedule
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.canCommitSpearKillTerminalLunge
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.captureSpearKillRouteTargetSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.isSpearKillTerminalAimAligned
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.hasValidAStarTerminalAttackRay
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.terminalSuffixSteps
import net.ccbluex.liquidbounce.utils.client.player
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.remainingSpearKillTerminalSchedule(): SpearKillPathSchedule? {
    val settings = packetSessionSettings ?: return null
    val terminalSteps = packetBootSession.terminalSuffixSteps
    return buildSpearKillTerminalSchedule(
        terminalStepCount = terminalSteps,
        stepWaitTicks = settings.stepWaitTicks,
        strikeHoldTicks = settings.strikeHoldTicks,
    )
}

internal fun SpearKillModuleState.hasSafeLiveAStarTerminalCommit(target: LivingEntity): Boolean {
    val approach = plannedAStarApproach ?: return false
    val remainingSchedule = remainingSpearKillTerminalSchedule() ?: return false
    val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return false
    val prediction = captureSpearKillRouteTargetSnapshot(target)
        .predict(remainingSchedule.hitTick)
    val eyeOffset = player.eyePosition.subtract(player.position())
    val virtualEye = approach.terminalWaypoint.add(eyeOffset)
    val terminalMovement = approach.terminalWaypoint.subtract(approach.plannerGoal)

    return canCommitSpearKillTerminalLunge(
        isUsingSpear = isUsingSpear,
        ticksUsingItem = player.ticksUsingItem,
        delayTicks = kineticWeapon.delayTicks,
        damageUseDuration = kineticWeapon.computeDamageUseDuration(),
        remainingHitTicks = remainingSchedule.hitTick,
        hasLiveAttackRay = hasValidAStarTerminalAttackRay(
            targetBox = prediction.boundingBox,
            eyeOffset = eyeOffset,
            approach = approach,
        ),
        aimAligned = isSpearKillTerminalAimAligned(
            eye = virtualEye,
            terminalMovement = terminalMovement,
            targetPoint = prediction.eyePosition,
        ),
    )
}
