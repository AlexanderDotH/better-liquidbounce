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
package net.ccbluex.liquidbounce.features.global

import com.google.gson.JsonParser
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlobalSettingsClientChatMigrationTest {

    @Test
    fun `legacy provider settings move under LiquidBounceFDP and canonical values win`() {
        val settings = JsonParser.parseString(
            """
            {
              "name": "ClientChat",
              "value": [
                {"name":"Enabled","value":false},
                {"name":"AutoTranslate","value":["PublicChat"]},
                {"name":"JwtToken","value":"legacy-token"},
                {"name":"FutureSetting","value":42},
                {"name":"LiquidBounceFDP","value":[
                  {"name":"Enabled","value":true},
                  {"name":"AutoTranslate","value":["PrivateChat"]}
                ]}
              ]
            }
            """.trimIndent()
        ).asJsonObject

        assertTrue(ClientChatSettingsMigration.migrate(settings))

        val rootValues = settings.getAsJsonArray("value").map { it.asJsonObject }
        assertEquals(listOf("Enabled", "FutureSetting", "LiquidBounceFDP"), rootValues.map { it["name"].asString })
        val providerValues = rootValues.single { it["name"].asString == "LiquidBounceFDP" }
            .getAsJsonArray("value")
            .map { it.asJsonObject }
            .associate { it["name"].asString to it["value"] }
        assertEquals(listOf("PrivateChat"), providerValues.getValue("AutoTranslate").asJsonArray.map { it.asString })
        assertEquals("legacy-token", providerValues.getValue("JwtToken").asString)
    }

    @Test
    fun `settings source keeps aliases providers hidden JWT and AxoChat route`() {
        val source = Path.of(
            "src/main/kotlin/net/ccbluex/liquidbounce/features/global/GlobalSettingsClientChat.kt"
        ).toFile().readText()

        assertTrue(source.contains("name = \"ClientChats\""))
        assertTrue(source.contains("aliases = listOf(\"ClientChat\", \"GlobalChat\", \"IRC\")"))
        assertTrue(source.contains("object LiquidBounceFDP : ToggleableValueGroup"))
        assertFalse(source.contains("object Essential : ToggleableValueGroup"))
        assertTrue(source.contains("text(\"JwtToken\", \"\").notAnOption()"))
        assertTrue(source.contains("ChatNetwork.LIQUIDBOUNCE"))
        assertTrue(source.contains("ChatNetwork.FDPCLIENT"))
        assertFalse(source.contains("ChatNetwork.AXOCHAT"))
        assertTrue(source.contains("chatClient.supportsClientChannels"))
        assertFalse(source.contains("translation(\"liquidbounce.liquidchat.states.disconnected\")"))
        assertTrue(source.contains("chatClient.connect()\n        }\n        delay(30.seconds)"))
    }
}
