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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.SpearKillSpeedStep
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.resetSpearKillSpeedSession() {
    speedController.reset()
    lastRequestedStep = SpearKillSpeedStep(0.0, 0.0)
    lastDeliveredMovement = Vec3.ZERO
    lastDeliveredOutboundMovement = Vec3.ZERO
    terminalBurstDeliveredMovementThisTick = Vec3.ZERO
}
