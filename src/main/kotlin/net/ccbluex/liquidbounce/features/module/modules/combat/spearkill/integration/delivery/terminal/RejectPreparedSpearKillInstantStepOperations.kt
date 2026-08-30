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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.InstantStepDelivery
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.PacketFollowTermination
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillInstantRejectedStepAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.resolveSpearKillInstantRejectedStepAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.failActivePrimedStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.terminatePacketFollow

internal fun SpearKillModuleState.rejectPreparedSpearKillInstantStep(
    outboundStep: Boolean,
    packetsSent: Int,
): InstantStepDelivery {
    confirmRemoteSpearKillPacketStep(delivered = false)
    plannedPacket = null
    awaitingVanillaMovementPacket = false
    failActivePrimedStep()
    return recoverRejectedSpearKillInstantStep(outboundStep, packetsSent)
}
internal fun SpearKillModuleState.recoverRejectedSpearKillInstantStep(
    outboundStep: Boolean,
    packetsSent: Int,
): InstantStepDelivery {
    val action = resolveSpearKillInstantRejectedStepAction(
        outboundStep = outboundStep,
        recovering = packetBootSession.recovering,
    )
    when (action) {
        SpearKillInstantRejectedStepAction.TERMINATE_OUTBOUND ->
            terminatePacketFollow(lockedAStarTarget, PacketFollowTermination.BLOCKED)
        SpearKillInstantRejectedStepAction.REPLAN_RETURN ->
            replanRejectedSpearKillInstantReturn()
        SpearKillInstantRejectedStepAction.PAUSE -> Unit
    }
    return InstantStepDelivery(
        packetsSent = packetsSent,
        continueBurst = action == SpearKillInstantRejectedStepAction.TERMINATE_OUTBOUND &&
            packetBootSession.active,
    )
}
