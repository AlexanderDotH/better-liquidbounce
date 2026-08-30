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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.common.debug.DebugParameterSink
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillAttemptSnapshot
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.SpearKillKineticDamageEstimate
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.SpearKillKineticSpeedEstimate
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.activeStepLimit
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.currentVanillaMovementBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.estimateSpearKillKineticDamage
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.estimateSpearKillKineticSpeed
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.entity.lastPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.updateSpearKillAttemptEvidence() {
    if (damageEvidenceTracker.expire(player.tickCount) && attemptRouteCompleted) {
        completeSpearKillAttempt("damage-window-expired")
        attemptRouteCompleted = false
    }

    val snapshot = attemptTracker.current ?: attemptTracker.lastCompleted
    publishSpearKillAttemptDebug(snapshot)
    publishSpearKillMovementDebug()
    publishSpearKillKineticDebug()
}

private fun SpearKillModuleState.publishSpearKillAttemptDebug(snapshot: SpearKillAttemptSnapshot?) {
    DebugParameterSink.publish(this, "Attempt Target") { snapshot?.targetName }
    DebugParameterSink.publish(this, "Attempt Target Source") { snapshot?.targetSource }
    DebugParameterSink.publish(this, "Attempt Route") { snapshot?.plannedRouteMode }
    DebugParameterSink.publish(this, "Attempt Outbound Steps") {
        snapshot?.let { "${it.outboundStepCount}/${it.plannedOutboundStepCount}" }
    }
    DebugParameterSink.publish(this, "Attempt Predicted Hit Tick") { snapshot?.predictedHitTick }
    DebugParameterSink.publish(this, "Attempt Charge Ticks") { snapshot?.chargeTicks }
    DebugParameterSink.publish(this, "Attempt Terminal Authorization Tick") { snapshot?.terminalAuthorizationTick }
    DebugParameterSink.publish(this, "Attempt Setback") { snapshot?.setback }
    DebugParameterSink.publish(this, "Attempt Blocked Edge") { snapshot?.blocked }
    DebugParameterSink.publish(this, "Attempt Recovery") { snapshot?.recovery }
    DebugParameterSink.publish(this, "Attempt Target Defeated") { snapshot?.defeated }
    DebugParameterSink.publish(this, "Attempt Target Removed") { snapshot?.targetRemoved }
    DebugParameterSink.publish(this, "Attempt Damage Evidence") { snapshot?.damageEvidence }
    DebugParameterSink.publish(this, "Attempt Outcome") { snapshot?.outcome }
}

private fun SpearKillModuleState.publishSpearKillMovementDebug() {
    DebugParameterSink.publish(this, "Target Speed") { movementConfiguration.targetSpeed }
    DebugParameterSink.publish(this, "Current Speed") { speedController.currentSpeed }
    DebugParameterSink.publish(this, "Acceleration") { movementConfiguration.acceleration }
    DebugParameterSink.publish(this, "Deceleration") { movementConfiguration.deceleration }
    DebugParameterSink.publish(this, "Step Distance") { activeMovementTransport?.stepLimit ?: activeStepLimit }
    DebugParameterSink.publish(this, "Estimated Vanilla Budget") { currentVanillaMovementBudget }
    DebugParameterSink.publish(this, "Requested Displacement") { lastRequestedStep.stepLimit }
    DebugParameterSink.publish(this, "Delivered Displacement") { lastDeliveredMovement.length() }
    DebugParameterSink.publish(this, "Owned Movement Packets Previous Tick") { ownedMovementPacketsThisTick }
    DebugParameterSink.publish(this, "Server Correction") {
        lastServerCorrectionTick?.let { player.tickCount - it <= 1 } ?: false
    }
    DebugParameterSink.publish(this, "Look Vector") { player.lookAngle }
    DebugParameterSink.publish(this, "Move Direction") { lastDeliveredMovement.normalize() }
}

private fun SpearKillModuleState.publishSpearKillKineticDebug() {
    DebugParameterSink.publish(this, "Estimated Attacker Kinetic Speed") {
        currentSpearKillKineticEstimate().attackerSpeed
    }
    DebugParameterSink.publish(this, "Estimated Relative Kinetic Speed") {
        currentSpearKillKineticEstimate().relativeSpeed
    }
    DebugParameterSink.publish(this, "Estimated Kinetic Bonus Damage") {
        currentSpearKillKineticDamageEstimate()?.bonusDamage
    }
    DebugParameterSink.publish(this, "Kinetic Requirements Met") {
        currentSpearKillKineticDamageEstimate()?.meetsRequirements
    }
}

internal fun SpearKillModuleState.currentSpearKillKineticEstimate(): SpearKillKineticSpeedEstimate {
    val targetMovement = lockedAStarTarget?.let { it.position().subtract(it.lastPos) } ?: Vec3.ZERO
    return estimateSpearKillKineticSpeed(lastDeliveredOutboundMovement, targetMovement, player.lookAngle)
}

internal fun SpearKillModuleState.currentSpearKillKineticDamageEstimate(): SpearKillKineticDamageEstimate? {
    val kineticWeapon = player.useItem.get(DataComponents.KINETIC_WEAPON) ?: return null
    val requirements = spearKillKineticDamageRequirements(kineticWeapon) ?: return null
    val targetMovement = lockedAStarTarget?.let { it.position().subtract(it.lastPos) } ?: Vec3.ZERO
    return estimateSpearKillKineticDamage(
        deliveredMovement = lastDeliveredOutboundMovement,
        targetMovement = targetMovement,
        lookDirection = player.lookAngle,
        requirements = requirements,
    )
}
