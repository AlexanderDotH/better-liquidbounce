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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpearKillCanonicalInstantRoutingPacketCapSurviveMigrationTest {

    @Test
    fun `canonical Instant routing and packet cap survive migration`() {
        val config = JsonParser.parseString(
            """
            {
              "name": "SpearKill",
              "value": [{
                "name": "Movement",
                "active": "Packet",
                "value": [],
                "choices": {
                  "Motion": { "name": "Motion", "value": [] },
                  "Packet": {
                    "name": "Packet",
                    "value": [{
                      "name": "Routing",
                      "active": "Instant",
                      "value": [],
                      "choices": {
                        "Direct": { "name": "Direct", "value": [] },
                        "AStar": { "name": "AStar", "value": [] },
                        "NetworkOptimized": { "name": "NetworkOptimized", "value": [] },
                        "Instant": {
                          "name": "Instant",
                          "value": [{ "name": "MaxPackets", "value": 192 }]
                        }
                      }
                    }]
                  }
                }
              }]
            }
            """.trimIndent(),
        ).asJsonObject

        migrateLegacySpearKillConfig(config)

        val routing = config.valuesByName().getValue("Movement")
            .choice("Packet").valuesByName().getValue("Routing")
        assertEquals("Instant", routing["active"].asString)
        assertEquals(
            192,
            routing.choice("Instant").valuesByName().getValue("MaxPackets")["value"].asInt,
        )
    }
}
