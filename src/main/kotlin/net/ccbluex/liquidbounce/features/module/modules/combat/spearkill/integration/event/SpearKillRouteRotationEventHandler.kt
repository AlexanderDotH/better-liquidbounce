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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.event.events.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.activeRouteHeading
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.spearKillRouteRotationTarget
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention.OBJECTION_AGAINST_EVERYTHING
import net.ccbluex.liquidbounce.utils.kotlin.Priority


internal fun SpearKillModuleState.registerRouteRotationHandler() {
    handler<RotationUpdateEvent>(
    priority = OBJECTION_AGAINST_EVERYTHING,
) {
    val heading = activeRouteHeading ?: return@handler
    RotationManager.setRotationTarget(
        plan = spearKillRouteRotationTarget(heading),
        priority = Priority.IMPORTANT_FOR_USER_SAFETY,
        provider = this@registerRouteRotationHandler,
    )
    }
}
