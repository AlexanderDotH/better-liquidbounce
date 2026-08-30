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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpearKillCompatibilityGuardTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `shared routing extraction leaves SpearKill order and defaults unchanged`() {
        ModuleSpearKill.restore()
        try {
            assertEquals(
                listOf(
                    "Hidden",
                    "TargetDistance",
                    "Activation",
                    "TargetSource",
                    "Movement",
                    "SneakWhileMoving",
                    "ElytraWhileMoving",
                    "Preview",
                ),
                ModuleSpearKill.inner.dropWhile { it.name != "Hidden" }.map { it.name },
            )
            assertEquals(
                SpearKillActivationMode.Manual,
                ModuleSpearKill.inner.single { it.name == "Activation" }.get(),
            )
            assertEquals(
                SpearKillTargetSource.Crosshair,
                ModuleSpearKill.inner.single { it.name == "TargetSource" }.get(),
            )

            val movement = ModuleSpearKill.inner.single { it.name == "Movement" } as ModeValueGroup<*>
            val packet = movement.modes.single { it.name == "Packet" }
            val routing = packet.inner.single { it.name == "Routing" } as ModeValueGroup<*>
            assertEquals("Packet", movement.activeMode.name)
            assertEquals("Direct", routing.activeMode.name)
            assertEquals(
                listOf("Direct", "AStar", "NetworkOptimized", "Instant"),
                routing.modes.map { it.name },
            )
        } finally {
            ModuleSpearKill.restore()
        }
    }

    @Test
    fun `shared routing extraction leaves legacy SpearKill migration idempotent`() {
        val legacy = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [
                { "name": "MaxTargetDistance", "value": 123.0 },
                { "name": "TargetSource", "value": "LookRay" },
                { "name": "ServerSneak", "value": true }
              ]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillConfig(legacy)
        val firstResult = legacy.deepCopy()
        migrateLegacySpearKillConfig(legacy)

        assertEquals(firstResult, legacy)
        val values = legacy.getAsJsonArray("value").map { it.asJsonObject }.associateBy { it["name"].asString }
        assertEquals(123f, values.getValue("TargetDistance")["value"].asFloat)
        assertEquals("Crosshair", values.getValue("TargetSource")["value"].asString)
        assertEquals("Packet", values.getValue("SneakWhileMoving")["value"].asString)
    }
}
