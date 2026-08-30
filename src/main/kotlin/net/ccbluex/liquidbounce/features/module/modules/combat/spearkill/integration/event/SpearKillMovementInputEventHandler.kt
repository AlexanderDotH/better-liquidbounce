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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.SpearKillMovementInput
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.applySpearKillMovementInputLease
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.movementAssistLease
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.SAFETY_FEATURE


internal fun SpearKillModuleState.registerMovementInputHandler() {
    handler<MovementInputEvent>(priority = SAFETY_FEATURE) { event ->
    val applied = applySpearKillMovementInputLease(
        physical = SpearKillMovementInput(event.jump, event.sneak),
        lease = movementAssistLease,
    )
    event.jump = applied.jump
    event.sneak = applied.sneak
    }
}
