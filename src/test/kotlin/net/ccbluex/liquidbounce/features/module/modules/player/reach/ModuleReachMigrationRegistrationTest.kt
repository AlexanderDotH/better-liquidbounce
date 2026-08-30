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
package net.ccbluex.liquidbounce.features.module.modules.player.reach

import com.google.gson.JsonParser
import net.ccbluex.liquidbounce.config.ConfigMigrationRegistry
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModuleReachMigrationRegistrationTest {

    @Test
    fun `initializing Reach registers its legacy migration in the config load pipeline`() {
        val root = JsonParser.parseString(
            """
            {
              "name": "modules",
              "value": [{
                "name": "SuperHit",
                "value": [
                  {"name": "Enabled", "value": true},
                  {"name": "MaxRange", "value": 120.0}
                ]
              }]
            }
            """.trimIndent(),
        ).asJsonObject

        assertEquals("Reach", ModuleReach.name)
        ConfigMigrationRegistry.applyAll(root)

        val modules = root["value"].asJsonArray.map { it.asJsonObject }
        val reach = modules.single { it["name"].asString == "Reach" }
        val hit = reach["value"].asJsonArray
            .map { it.asJsonObject }
            .single { it["name"].asString == "Hit" }

        assertTrue(hit["value"].asJsonArray.any { setting ->
            setting.asJsonObject["name"].asString == "MaxRange" &&
                setting.asJsonObject["value"].asDouble == 120.0
        })
        assertNull(modules.singleOrNull { it["name"].asString == "SuperHit" })
    }
}
