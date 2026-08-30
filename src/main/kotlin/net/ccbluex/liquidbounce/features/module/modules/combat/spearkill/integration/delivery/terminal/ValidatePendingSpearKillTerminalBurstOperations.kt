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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_RECOVERY_STEP_EPSILON
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPendingPacketStepValidation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.isSpearKillPacketStepClear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.previewSpearKillOutboundStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.terminatePacketFollow
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.pendingTerminalBurstMovement

/** Revalidates the complete same-tick displacement, not merely each fall-safe wire segment. */
internal fun SpearKillModuleState.validatePendingSpearKillTerminalBurst(): SpearKillPendingPacketStepValidation {
    val movement = packetBootSession.pendingTerminalBurstMovement
        ?: return SpearKillPendingPacketStepValidation.CLEAR
    val sessionOrigin = packetSessionOrigin ?: return SpearKillPendingPacketStepValidation.BLOCKED
    val outboundStepLimit = previewSpearKillOutboundStep().stepLimit
    if (movement.length() > outboundStepLimit + SPEAR_KILL_RECOVERY_STEP_EPSILON) {
        return SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED
    }
    val sessionBoundingBox = spearKillServerCollisionBoxAt(sessionOrigin)
    val segmentValidator = if (packetAStarAttackActive) {
        createServerMovementSpearKillSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
    } else {
        createServerValidatedSpearKillDirectPacketSegmentValidator(
            origin = sessionOrigin,
            playerBoundingBox = sessionBoundingBox,
        )
    }
    return if (isSpearKillPacketStepClear(
            sessionOrigin = sessionOrigin,
            committedOffset = packetBootSession.committedOffset,
            candidateOffset = packetBootSession.committedOffset.add(movement),
            maxStepLength = outboundStepLimit,
            segmentValidator = segmentValidator,
        )
    ) {
        SpearKillPendingPacketStepValidation.CLEAR
    } else {
        SpearKillPendingPacketStepValidation.BLOCKED
    }
}
internal fun SpearKillModuleState.rejectPendingSpearKillPacketStep(validation: SpearKillPendingPacketStepValidation) {
    val outboundStep = packetBootSession.pendingOutboundStep
    confirmRemoteSpearKillPacketStep(delivered = false)
    plannedPacket = null
    awaitingVanillaMovementPacket = false
    if (validation == SpearKillPendingPacketStepValidation.BUDGET_EXCEEDED && outboundStep) {
        replanPacketRouteForCurrentBudget()
    } else if (validation == SpearKillPendingPacketStepValidation.BLOCKED && outboundStep) {
        terminatePacketFollow(lockedAStarTarget, PacketFollowTermination.BLOCKED)
    }
}
