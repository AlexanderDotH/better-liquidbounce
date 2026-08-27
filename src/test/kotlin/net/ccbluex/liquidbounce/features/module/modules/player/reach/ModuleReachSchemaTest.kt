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

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleReach
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ModuleReachSchemaTest {

    @Test
    fun `existing Reach settings retain exact positions defaults and ranges`() {
        assertEquals(
            listOf("Enabled", "Bind", "Hidden", "Entity", "BlockRangeIncrease", "Hit", "Interactable"),
            ModuleReach.inner.map { it.name },
        )
        assertEquals(listOf("RangeIncrease", "ThroughWallsRange"), ModuleReach.entity.inner.map { it.name })
        ModuleReach.entity.inner.single { it.name == "RangeIncrease" }.assertRange(1f, 0f, 5f)
        ModuleReach.entity.inner.single { it.name == "ThroughWallsRange" }.assertRange(0f, 0f, 8f)
        ModuleReach.inner.single { it.name == "BlockRangeIncrease" }.assertRange(0.5f, 0f, 64f)
    }

    @Test
    fun `new Reach branches default off and SuperHit resolves as compatibility alias`() {
        val hit = ModuleReach.inner.single { it.name == "Hit" } as ToggleableValueGroup
        val interactable = ModuleReach.inner.single { it.name == "Interactable" } as ToggleableValueGroup

        assertFalse(hit.enabled)
        assertFalse(interactable.enabled)
        assertTrue("SuperHit" in ModuleReach.aliases)
    }

    private fun Any.assertRange(default: Number, minimum: Number, maximum: Number) {
        this as RangedValue<*>
        assertEquals(default.toDouble(), (get() as Number).toDouble(), 0.0)
        assertEquals(minimum.toDouble(), (range.start as Number).toDouble(), 0.0)
        assertEquals(maximum.toDouble(), (range.endInclusive as Number).toDouble(), 0.0)
    }

    private companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
