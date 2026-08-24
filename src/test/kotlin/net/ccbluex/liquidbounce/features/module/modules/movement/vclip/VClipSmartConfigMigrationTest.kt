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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VClipSmartConfigMigrationTest {

    @Test
    fun `legacy Smart bedrock safety moves to the VClip module and preserves false`() {
        val vClip = JsonParser.parseString(
            """{"name":"VClip","value":[
                {"name":"Target","active":"Smart","value":[],"choices":{
                    "Distance":{"name":"Distance","value":[]},
                    "Smart":{"name":"Smart","value":[
                        {"name":"DoNotClipAroundBedrock","value":false}
                    ]}
                }}
            ]}""",
        ).asJsonObject

        migrateLegacyVClipBedrockSafety(vClip)

        val values = vClip.getAsJsonArray("value").map { it.asJsonObject }
        val safety = values.single { it["name"].asString == "DoNotClipAroundBedrock" }
        val target = values.single { it["name"].asString == "Target" }
        val smart = target.getAsJsonObject("choices").getAsJsonObject("Smart")

        assertEquals(false, safety["value"].asBoolean)
        assertEquals(emptyList<String>(), smart.getAsJsonArray("value").map { it.asJsonObject["name"].asString })
    }

    @Test
    fun `canonical module bedrock safety wins and removes a stale Smart value`() {
        val vClip = JsonParser.parseString(
            """{"name":"VClip","value":[
                {"name":"DoNotClipAroundBedrock","value":true},
                {"name":"Target","active":"Smart","value":[],"choices":{
                    "Distance":{"name":"Distance","value":[]},
                    "Smart":{"name":"Smart","value":[
                        {"name":"DoNotClipAroundBedrock","value":false}
                    ]}
                }}
            ]}""",
        ).asJsonObject

        migrateLegacyVClipBedrockSafety(vClip)

        val values = vClip.getAsJsonArray("value").map { it.asJsonObject }
        val safetyValues = values.filter { it["name"].asString == "DoNotClipAroundBedrock" }
        val target = values.single { it["name"].asString == "Target" }
        val smart = target.getAsJsonObject("choices").getAsJsonObject("Smart")

        assertEquals(1, safetyValues.size)
        assertEquals(true, safetyValues.single()["value"].asBoolean)
        assertEquals(emptyList<String>(), smart.getAsJsonArray("value").map { it.asJsonObject["name"].asString })
    }

    @Test
    fun `legacy direct MaxDistance becomes an enabled ScanDistance group`() {
        val smart = JsonParser.parseString(
            """{"name":"Smart","value":[{"name":"MaxDistance","value":20}]}""",
        ).asJsonObject

        VClipSmartTarget.prepareDeserialize(smart)

        val values = smart.getAsJsonArray("value").map { it.asJsonObject }
        assertEquals(listOf("ScanDistance"), values.map { it["name"].asString })
        assertEquals(
            listOf("Enabled", "MaxDistance"),
            values.single().getAsJsonArray("value").map { it.asJsonObject["name"].asString },
        )
        assertEquals(true, values.single().getAsJsonArray("value")[0].asJsonObject["value"].asBoolean)
        assertEquals(20, values.single().getAsJsonArray("value")[1].asJsonObject["value"].asInt)
    }

    @Test
    fun `new ScanDistance group wins over a stale direct MaxDistance`() {
        val smart = JsonParser.parseString(
            """{"name":"Smart","value":[
                {"name":"ScanDistance","value":[
                    {"name":"Enabled","value":false},
                    {"name":"MaxDistance","value":7}
                ]},
                {"name":"MaxDistance","value":20}
            ]}""",
        ).asJsonObject

        migrateLegacyVClipSmartScanDistance(smart)

        val values = smart.getAsJsonArray("value").map { it.asJsonObject }
        assertEquals(listOf("ScanDistance"), values.map { it["name"].asString })
        assertEquals(false, values.single().getAsJsonArray("value")[0].asJsonObject["value"].asBoolean)
        assertEquals(7, values.single().getAsJsonArray("value")[1].asJsonObject["value"].asInt)
    }
}
