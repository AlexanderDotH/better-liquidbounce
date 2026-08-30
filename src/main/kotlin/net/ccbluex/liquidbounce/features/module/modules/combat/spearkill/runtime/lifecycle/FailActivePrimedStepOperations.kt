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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.debugSpearKill
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugSessionFields
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.debug.spearKillDebugVector
import net.ccbluex.liquidbounce.utils.client.player

internal fun SpearKillModuleState.failActivePrimedStep() {
    activePrimedStep?.let { step ->
        debugSpearKill("PRIMED_BURST_DELIVERY_FAILED") {
            listOf(
                "tick" to player.tickCount,
                "burst_id" to step.burstId,
                "origin" to spearKillDebugVector(step.origin),
                "destination" to spearKillDebugVector(step.destination),
                "no_fall_required" to step.noFallPacketRequired,
                "no_fall_delivered" to step.noFallPacketDelivered,
            ) + spearKillDebugSessionFields()
        }
    }
    activePrimedStep?.burstId?.let(highSpeedResearch::recordDeliveryFailure)
    activePrimedStep = null
}
