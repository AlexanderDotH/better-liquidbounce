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
package net.ccbluex.liquidbounce.features.module.modules.render.esp

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EspCombatPresentationBoundaryTest {

    @Test
    fun `module resolves combat presentation through its live runtime port`() {
        val module = source("features/module/modules/render/esp/ModuleESP.kt")
        val colorResolution = module.substringAfter("fun getColor(entity: LivingEntity): Color4b")
            .substringBefore("/**")

        assertFalse(module.contains("features.combat.runtime"))
        assertOrdered(
            colorResolution,
            "entity.hurtTime > 0",
            "entity.isInvisible",
            "FriendManager.isFriend(entity)",
            "EspModeRuntime.taggedColor(entity)",
            "colorModes.activeMode.getColor(entity)",
        )
        assertTrue(module.contains(
            "modes.activeMode.requiresTrueSight && EspModeRuntime.shouldBeShown(entity)"
        ))
    }

    @Test
    fun `integration adapter installs live combat providers`() {
        val adapter = source(
            "features/module/modules/render/esp/integration/EspMaskFeatureAdapter.kt"
        )
        val installation = adapter.substringAfter("fun installCombatPresentation()")
            .substringBefore("override fun forEntity")

        assertTrue(installation.contains("EspModeRuntime.installCombatPresentation("))
        assertTrue(installation.contains("EntityTaggingManager.getTag(entity).color"))
        assertTrue(installation.contains("entity.shouldBeShown()"))
    }

    private fun assertOrdered(source: String, vararg markers: String) {
        var previous = -1
        markers.forEach { marker ->
            val index = source.indexOf(marker)
            assertTrue(index > previous, "$marker must retain its relative order")
            previous = index
        }
    }

    private fun source(relativePath: String): String = Path.of(
        "src/main/kotlin/net/ccbluex/liquidbounce/$relativePath"
    ).readText()
}
