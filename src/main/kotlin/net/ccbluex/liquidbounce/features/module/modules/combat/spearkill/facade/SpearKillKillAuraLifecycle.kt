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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.facade


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.SpearKillKillAuraReleaseAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.hasKillAuraSpearUseRequest
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.killAuraOwnsAttempt
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.resolveSpearKillKillAuraReleaseAction
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.beginKillAuraOwnedReturn
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.cancelKillAuraPreparation
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.cleanup.clearKillAuraSpearUse

internal fun SpearKillModuleState.onKillAuraDisabled() {
    val action = resolveSpearKillKillAuraReleaseAction(
        killAuraOwnsAttempt = killAuraOwnsAttempt,
        killAuraPreparationActive = packetRoutePreparationActive &&
            (pendingKillAuraTarget != null || killAuraSpearTarget != null),
        inheritedUseActive = hasKillAuraSpearUseRequest || killAuraStartedSpearUse,
    )
    when (action) {
        SpearKillKillAuraReleaseAction.NONE -> return
        SpearKillKillAuraReleaseAction.RELEASE_INHERITED_USE -> clearKillAuraSpearUse()
        SpearKillKillAuraReleaseAction.CANCEL_INHERITED_PREPARATION -> {
            cancelKillAuraPreparation()
            clearKillAuraSpearUse()
        }
        SpearKillKillAuraReleaseAction.CANCEL_INHERITED_ROUTE -> beginKillAuraOwnedReturn()
    }
}
