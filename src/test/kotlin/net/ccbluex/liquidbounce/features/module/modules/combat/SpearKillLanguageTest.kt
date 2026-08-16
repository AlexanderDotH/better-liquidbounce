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
package net.ccbluex.liquidbounce.features.module.modules.combat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class SpearKillLanguageTest {

    @Test
    fun `unified movement contract is documented without retired controls`() {
        listOf("en_us", "de_de").forEach { locale ->
            val translations = readLocale(locale)
            assertTrue(translations.keySet().containsAll(REQUIRED_KEYS), locale)
            RETIRED_KEYS.forEach { key -> assertFalse(translations.has(key), "$locale: $key") }

            val spearKillText = translations.entrySet()
                .filter { it.key.startsWith("liquidbounce.module.spearKill") }
                .joinToString(" ") { it.value.asString }
            assertTrue(
                translations["liquidbounce.module.spearKill.movement.targetSpeed.warning"]
                    .asString.lowercase().contains("server"),
                "$locale: TargetSpeed warning must mention server authority",
            )
            listOf(
                "Adaptive",
                "LookRay",
                "StepLimit",
                "StepsPerTeleport",
                "WaitBeforeTeleport",
                "10-block-per-tick cap",
                "Limit von 10 Blöcken pro Tick",
            ).forEach { staleTerm ->
                assertFalse(spearKillText.contains(staleTerm), "$locale: $staleTerm")
            }
        }
    }

    private fun readLocale(locale: String): JsonObject {
        val resource = checkNotNull(
            javaClass.classLoader.getResourceAsStream("resources/liquidbounce/lang/$locale.json"),
        )
        return resource.use { JsonParser.parseReader(InputStreamReader(it)).asJsonObject }
    }

    private companion object {
        val REQUIRED_KEYS = setOf(
            "liquidbounce.module.spearKill.description",
            "liquidbounce.module.spearKill.targetDistance.description",
            "liquidbounce.module.spearKill.activation.description",
            "liquidbounce.module.spearKill.targetSource.description",
            "liquidbounce.module.spearKill.movement.targetSpeed.description",
            "liquidbounce.module.spearKill.movement.targetSpeed.warning",
            "liquidbounce.module.spearKill.movement.acceleration.description",
            "liquidbounce.module.spearKill.movement.deceleration.description",
            "liquidbounce.module.spearKill.movement.motion.stepDistance.description",
            "liquidbounce.module.spearKill.movement.packet.stepDistance.description",
            "liquidbounce.module.spearKill.movement.packet.stepDelay.description",
            "liquidbounce.module.spearKill.movement.packet.routing.description",
            "liquidbounce.module.spearKill.movement.packet.routing.aStar.extendedDescription",
            "liquidbounce.module.spearKill.movement.packet.routing.aStar.maxCost.description",
            "liquidbounce.module.spearKill.movement.packet.routing.aStar.diagonal.description",
            "liquidbounce.module.spearKill.movement.packet.routing.aStar.lineOfSightShortcuts.description",
            "liquidbounce.module.spearKill.movement.packet.routing.networkOptimized.extendedDescription",
            "liquidbounce.module.spearKill.movement.packet.routing.networkOptimized.maxSpeed.description",
            "liquidbounce.module.spearKill.movement.packet.routing.networkOptimized.minimumStepDelay.description",
            "liquidbounce.module.spearKill.movement.packet.routing.networkOptimized.setbackBackoff.description",
            "liquidbounce.module.spearKill.movement.packet.routing.networkOptimized.maxCost.description",
            "liquidbounce.module.spearKill.movement.packet.routing.networkOptimized.diagonal.description",
            "liquidbounce.module.spearKill.movement.packet.routing.networkOptimized.lineOfSightShortcuts.description",
            "liquidbounce.module.spearKill.sneakWhileMoving.description",
            "liquidbounce.module.spearKill.elytraWhileMoving.description",
            "liquidbounce.module.spearKill.preview.renderPath.description",
            "liquidbounce.module.spearKill.messages.pathBlocked",
            "liquidbounce.module.spearKill.messages.targetUnreachable",
            "liquidbounce.settings.combat.delegateKillAuraAttacks.description",
        )

        val RETIRED_KEYS = setOf(
            "liquidbounce.module.spearKill.speed.description",
            "liquidbounce.module.spearKill.speed.warning",
            "liquidbounce.module.spearKill.maxSpeed.description",
            "liquidbounce.module.spearKill.serverSneak.description",
            "liquidbounce.module.spearKill.movement.motion.stepLimit.description",
            "liquidbounce.module.spearKill.movement.packet.stepLimit.description",
            "liquidbounce.module.spearKill.movement.packet.waitTicks.description",
            "liquidbounce.module.spearKill.movement.motion.stepsPerTeleport.description",
            "liquidbounce.module.spearKill.movement.packet.stepsPerTeleport.description",
            "liquidbounce.module.spearKill.movement.packet.waitBeforeTeleport.description",
            "liquidbounce.module.spearKill.movement.packet.elytra.description",
            "liquidbounce.module.spearKill.movement.packet.elytra.maxSpeed.description",
            "liquidbounce.module.spearKill.movement.packet.aStar.renderPath.description",
        )
    }
}
