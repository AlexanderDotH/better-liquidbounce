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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.awaitingTerminalCommitAuthorization
import net.minecraft.world.entity.LivingEntity

internal fun SpearKillModuleState.handlePendingTerminalCommit(target: LivingEntity): Boolean {
    if (!packetBootSession.awaitingTerminalCommitAuthorization) return false

    if (packetAStarAttackActive) {
        commitOrReplanAStarTerminal(target)
    } else {
        commitOrReplanDirectTerminal(target)
    }
    return true
}
