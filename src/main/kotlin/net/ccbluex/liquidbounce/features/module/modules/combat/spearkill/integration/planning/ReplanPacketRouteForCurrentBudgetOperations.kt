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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketRouteReplanResult
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.applyConfirmedPhysicalReturnPosition
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.recovery.beginSafeExactReturn
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearAttack
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.server.synchronizeSpearKillServerSneak

internal fun SpearKillModuleState.replanPacketRouteForCurrentBudget() {
    val target = lockedAStarTarget
    val sessionOrigin = packetSessionOrigin ?: run {
        clearAttack("budget-replan-without-origin")
        return
    }
    if (target == null) {
        beginSafeExactReturn()
        applyConfirmedPhysicalReturnPosition()
        return
    }
    val routeOrigin = sessionOrigin.add(packetBootSession.committedOffset)
    if (packetAStarAttackActive) {
        when (replanLockedAStarTarget(target, routeOrigin, sessionOrigin)) {
            SpearKillPacketRouteReplanResult.INSTALLED -> Unit
            SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE,
            SpearKillPacketRouteReplanResult.BLOCKED,
            -> {
                beginSafeExactReturn()
                applyConfirmedPhysicalReturnPosition()
            }
        }
    } else {
        when (installReplannedDirectPacketRoute(target, routeOrigin, sessionOrigin)) {
            SpearKillPacketRouteReplanResult.INSTALLED,
            SpearKillPacketRouteReplanResult.TRANSIENT_FAILURE,
            -> Unit
            SpearKillPacketRouteReplanResult.BLOCKED -> {
                beginSafeExactReturn()
                applyConfirmedPhysicalReturnPosition()
            }
        }
    }
    synchronizeSpearKillServerSneak()
}
