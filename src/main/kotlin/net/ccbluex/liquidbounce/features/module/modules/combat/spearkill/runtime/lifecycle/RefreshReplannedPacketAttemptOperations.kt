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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.packetRoutingMode
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.refreshReplannedPacketAttempt(
    target: LivingEntity,
    outboundSteps: Int,
    hitTicks: Int,
    terminalAuthorizationRequired: Boolean,
) {
    val previousAttempt = attemptTracker.current
    beginSpearKillAttempt(
        target = target,
        routeMode = previousAttempt?.plannedRouteMode ?: packetRoutingMode.tag,
        outboundSteps = outboundSteps,
        hitTicks = hitTicks,
        terminalAuthorizationRequired = terminalAuthorizationRequired,
        targetSourceOverride = previousAttempt?.targetSource,
    )
}
