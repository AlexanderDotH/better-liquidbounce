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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
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

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaceKillConfigurationCompatibilityTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `legacy MaceKill config keeps its first FallHeight option and stored value of 170`() {
        val options = ModuleMaceKill.inner.dropWhile { it.name != "Hidden" }.drop(1)
        val fallHeight = options.first()
        assertEquals("FallHeight", fallHeight.name)
        ModuleMaceKill.restore()

        try {
            ConfigSystem.deserializeValueGroup(
                ModuleMaceKill,
                JsonParser.parseString(
                    """{"name":"MaceKill","value":[{"name":"FallHeight","value":170}]}""",
                ),
            )

            assertEquals(170, fallHeight.get())
        } finally {
            ModuleMaceKill.restore()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `packet routing schema is Direct AStar Instant with bounded ClipReach inputs`() {
        val configuration = MaceKillMovementConfiguration(null)
        val routing = configuration.packet.routing
        val stepDelay = configuration.packet.inner.single { it.name == "StepDelay" }
        val instantSettings = configuration.packet.instant.inner.associateBy { it.name }

        assertEquals("Direct", routing.activeMode.name)
        assertEquals(listOf("Direct", "AStar", "Instant"), routing.modes.map { it.name })
        assertEquals(
            listOf("PrimingPackets", "ClearanceHeight", "MaxPackets"),
            configuration.packet.instant.inner.map { it.name },
        )
        assertEquals(9, configuration.packet.instant.primingPackets)
        assertEquals(0..18, (instantSettings.getValue("PrimingPackets") as RangedValue<Int>).range)
        assertEquals(99, configuration.packet.instant.clearanceHeight)
        assertEquals(4..128, (instantSettings.getValue("ClearanceHeight") as RangedValue<Int>).range)
        assertEquals(128, configuration.packet.instant.maxPackets)
        assertEquals(6..512, (instantSettings.getValue("MaxPackets") as RangedValue<Int>).range)
        assertEquals(0, stepDelay.get())
        assertEquals(0..4, (stepDelay as RangedValue<Int>).range)
        assertEquals(
            listOf(MaceKillRoutingMode.DIRECT, MaceKillRoutingMode.A_STAR, MaceKillRoutingMode.INSTANT),
            MaceKillRoutingMode.entries,
        )
    }
}
