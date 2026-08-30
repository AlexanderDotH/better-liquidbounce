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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpearKillActiveTargetFollowPolicyTest {

    @Test
    fun `combat rejection terminates before recovery can pause the route`() {
        var deferredChecks = 0

        assertEquals(
            SpearKillActiveTargetFollowDecision.TERMINATE_UNREACHABLE,
            resolveSpearKillActiveTargetFollow(
                isCombatSafe = false,
                isRecovering = { deferredChecks++; true },
                tracksPacketTarget = { deferredChecks++; false },
            ),
        )
        assertEquals(0, deferredChecks)
    }

    @Test
    fun `recovery and non tracking modes pause an otherwise valid target`() {
        assertEquals(
            SpearKillActiveTargetFollowDecision.PAUSE,
            resolveSpearKillActiveTargetFollow(
                isCombatSafe = true,
                isRecovering = { true },
                tracksPacketTarget = { true },
            ),
        )
        assertEquals(
            SpearKillActiveTargetFollowDecision.PAUSE,
            resolveSpearKillActiveTargetFollow(
                isCombatSafe = true,
                isRecovering = { false },
                tracksPacketTarget = { false },
            ),
        )
    }

    @Test
    fun `active eligible tracked target continues`() {
        assertEquals(
            SpearKillActiveTargetFollowDecision.CONTINUE,
            resolveSpearKillActiveTargetFollow(
                isCombatSafe = true,
                isRecovering = { false },
                tracksPacketTarget = { true },
            ),
        )
    }
}
