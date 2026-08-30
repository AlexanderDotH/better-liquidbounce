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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillSpeedStep
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.activeSpeedStepDistance

internal fun SpearKillModuleState.previewSpearKillOutboundStep(): SpearKillSpeedStep {
    beginSpearKillSpeedSession()
    return speedController.preview(currentSpeedLimits(activeSpeedStepDistance))
        .also { lastRequestedStep = it }
}

internal fun SpearKillModuleState.confirmSpearKillOutboundStep() {
    if (!speedController.active) return
    lastRequestedStep = speedController.confirmOutbound(
        currentSpeedLimits(activeSpeedStepDistance),
    )
}
