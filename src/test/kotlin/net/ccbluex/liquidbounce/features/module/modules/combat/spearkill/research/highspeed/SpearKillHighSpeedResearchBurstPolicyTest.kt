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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed


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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillHighSpeedResearchBurstPolicyTest {

    @Test
    fun `research target requires pending outbound movement in the same world`() {
        assertTrue(SpearKillHighSpeedResearchBurstPolicy.admitsTarget(pendingOutbound = true, sameWorld = true))
        assertFalse(SpearKillHighSpeedResearchBurstPolicy.admitsTarget(pendingOutbound = false, sameWorld = true))
        assertFalse(SpearKillHighSpeedResearchBurstPolicy.admitsTarget(pendingOutbound = true, sameWorld = false))
    }

    @Test
    fun `only a targeted final outbound step requires a visible terminal ray`() {
        assertTrue(SpearKillHighSpeedResearchBurstPolicy.terminalRaytraceClear(false, true, false))
        assertTrue(SpearKillHighSpeedResearchBurstPolicy.terminalRaytraceClear(true, false, false))
        assertTrue(SpearKillHighSpeedResearchBurstPolicy.terminalRaytraceClear(true, true, true))
        assertFalse(SpearKillHighSpeedResearchBurstPolicy.terminalRaytraceClear(true, true, false))
    }

    @Test
    fun `packet count resets strictly after the maximum server packet ordinal`() {
        assertFalse(SpearKillHighSpeedResearchBurstPolicy.packetCountReset(finalPacketOrdinal = 20, maximum = 20))
        assertTrue(SpearKillHighSpeedResearchBurstPolicy.packetCountReset(finalPacketOrdinal = 21, maximum = 20))
    }
}
