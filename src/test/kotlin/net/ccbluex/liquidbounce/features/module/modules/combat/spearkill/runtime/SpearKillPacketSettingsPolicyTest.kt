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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime



import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.policy.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.delivery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.control.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SpearKillAStarSessionSettings
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillNetworkBudget
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillPrimedInstantPacketType
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.SpearKillPrimedInstantPriming
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.SpearKillRoutingMode
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.SpearKillHighSpeedResearchFinalPacketType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillPacketSettingsPolicyTest {

    @Test
    fun `direct routing retains configured movement pacing and safe defaults`() {
        val aStar = aStarSettings()

        val settings = resolveSpearKillPacketSessionSettings(
            settingsInput(aStar = aStar),
        )

        assertEquals(10.0, settings.transport.maxSpeed, 1e-9)
        assertEquals(6.0, settings.transport.stepLimit, 1e-9)
        assertFalse(settings.transport.elytraActive)
        assertEquals(3, settings.stepWaitTicks)
        assertSame(aStar, settings.aStar)
        assertEquals(SPEAR_KILL_DAMAGE_EVIDENCE_WINDOW_TICKS, settings.damageEvidenceWindowTicks)
        assertEquals(40, settings.setbackBackoffTicks)
        assertTrue(settings.allowTerminalBurst)
        assertFalse(settings.primedInstant)
        assertFalse(settings.researchLog)
    }

    @Test
    fun `network budget caps speed and step while replacing its pacing safeguards`() {
        val settings = resolveSpearKillPacketSessionSettings(
            settingsInput(
                routingMode = SpearKillRoutingMode.NETWORK_OPTIMIZED,
                elytraActive = true,
                networkBudget = SpearKillNetworkBudget(
                    maxSpeed = 4.0,
                    stepWaitTicks = 5,
                    damageEvidenceWindowTicks = 7,
                    setbackBackoffTicks = 80,
                    allowTerminalBurst = false,
                ),
            ),
        )

        assertEquals(4.0, settings.transport.maxSpeed, 1e-9)
        assertEquals(4.0, settings.transport.stepLimit, 1e-9)
        assertTrue(settings.transport.elytraActive)
        assertEquals(5, settings.stepWaitTicks)
        assertEquals(7, settings.damageEvidenceWindowTicks)
        assertEquals(80, settings.setbackBackoffTicks)
        assertFalse(settings.allowTerminalBurst)
    }

    @Test
    fun `instant primed routing always waits zero ticks and keeps research selection`() {
        val settings = resolveSpearKillPacketSessionSettings(
            settingsInput(
                routingMode = SpearKillRoutingMode.INSTANT,
                primedStrategySelected = true,
            ),
        )

        assertEquals(0, settings.stepWaitTicks)
        assertTrue(settings.primedInstant)
        assertSame(SpearKillPrimedInstantPriming.Auto, settings.priming)
        assertEquals(SpearKillPrimedInstantPacketType.PositionRotation, settings.primingPacketType)
        assertTrue(settings.researchLog)
        assertEquals(12, settings.instantMaxPackets)
        assertEquals(
            SpearKillHighSpeedResearchFinalPacketType.POSITION_ROTATION,
            settings.finalPacketType,
        )
    }

    private fun settingsInput(
        routingMode: SpearKillRoutingMode = SpearKillRoutingMode.DIRECT,
        aStar: SpearKillAStarSessionSettings = aStarSettings(),
        elytraActive: Boolean = false,
        networkBudget: SpearKillNetworkBudget? = null,
        primedStrategySelected: Boolean = false,
    ) = SpearKillPacketSettingsPolicyInput(
        configuredSpeed = 10.0,
        configuredStepLimit = 6.0,
        elytraActive = elytraActive,
        configuredStepWaitTicks = 3,
        routingMode = routingMode,
        aStar = aStar,
        networkBudget = networkBudget,
        configuredSetbackBackoffTicks = 40,
        instantMaxPackets = 12,
        primedStrategySelected = primedStrategySelected,
        primingPacketType = SpearKillPrimedInstantPacketType.PositionRotation,
        primedResearchLog = true,
    )

    private fun aStarSettings() = SpearKillAStarSessionSettings(
        maxCost = 80,
        diagonal = true,
        lineOfSightShortcuts = false,
    )
}
