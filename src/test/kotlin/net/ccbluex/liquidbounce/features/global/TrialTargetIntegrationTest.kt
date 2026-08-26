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

import net.ccbluex.liquidbounce.config.gson.fileGson
import net.ccbluex.liquidbounce.config.gson.interopGson
import net.ccbluex.liquidbounce.features.command.Parameter
import net.ccbluex.liquidbounce.features.command.commands.client.CommandTargets
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.combat.Targets
import net.ccbluex.liquidbounce.utils.combat.trialMembershipDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrialTargetIntegrationTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `Trial and Hostile are terminally independent for recognized Trial mobs`() {
        assertEquals(true, setOf(Targets.TRIAL).trialMembershipDecision(isCurrentTrialMob = true))
        assertEquals(false, setOf(Targets.HOSTILE).trialMembershipDecision(isCurrentTrialMob = true))
        assertNull(setOf(Targets.HOSTILE).trialMembershipDecision(isCurrentTrialMob = false))
    }

    @Test
    fun `new Combat and Visual defaults include Trial with the external wire tag`() {
        assertEquals("Trial", Targets.TRIAL.tag)
        assertTrue(Targets.TRIAL in GlobalSettingsTarget.combatChoices.get())
        assertTrue(Targets.TRIAL in GlobalSettingsTarget.visualChoices.get())
        assertTrue(Targets.TRIAL in GlobalSettingsTarget.combatChoices.choices)
        assertTrue(Targets.TRIAL in GlobalSettingsTarget.visualChoices.choices)
    }

    @Test
    fun `ClickGUI interop exposes Trial choices but hides the migration marker`() {
        val interop = interopGson.toJsonTree(GlobalSettingsTarget).asJsonObject
        val values = interop.getAsJsonArray("value").associateBy { it.asJsonObject["name"].asString }
        val combatChoices = values.getValue("Combat").asJsonObject
            .getAsJsonArray("choices")
            .map { it.asString }

        assertTrue("Trial" in combatChoices)
        assertFalse(TrialTargetConfigMigration.MARKER_NAME in values)

        val fileNames = fileGson.toJsonTree(GlobalSettingsTarget).asJsonObject
            .getAsJsonArray("value")
            .map { it.asJsonObject["name"].asString }
        assertTrue(TrialTargetConfigMigration.MARKER_NAME in fileNames)
    }

    @Test
    fun `targets combat and visual commands parse and suggest the Trial wire tag`() {
        val command = CommandTargets.createCommand()

        listOf("combat", "visual").forEach { subcommandName ->
            val parameter = command.subcommands.single { it.name == subcommandName }
                .parameters.single() as Parameter<Targets>
            val parsed = requireNotNull(parameter.verifier).verifyAndParse("Trial")
            val suggestions = requireNotNull(parameter.autocompletionHandler)
                .autocomplete("Tri", listOf("targets", subcommandName, "Tri"))

            assertEquals(Targets.TRIAL, (parsed as Parameter.Verificator.Result.Ok).mappedResult)
            assertTrue("Trial" in suggestions)
        }
    }
}
