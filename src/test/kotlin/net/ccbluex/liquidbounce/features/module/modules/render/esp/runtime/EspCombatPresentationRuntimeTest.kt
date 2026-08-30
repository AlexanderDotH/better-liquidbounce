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
package net.ccbluex.liquidbounce.features.module.modules.render.esp.runtime

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sun.misc.Unsafe

class EspCombatPresentationRuntimeTest {

    @AfterEach
    fun resetCombatPresentation() {
        EspModeRuntime.installCombatPresentation(
            taggedColor = { null },
            shouldBeShown = { true },
        )
    }

    @Test
    fun `combat presentation values remain live after installation`() {
        val entity = livingEntityIdentity()
        var taggedColor: Color4b? = Color4b.RED
        var visible = true
        var colorRequests = 0
        var visibilityRequests = 0
        EspModeRuntime.installCombatPresentation(
            taggedColor = { requested ->
                assertSame(entity, requested)
                colorRequests++
                taggedColor
            },
            shouldBeShown = { requested ->
                assertSame(entity, requested)
                visibilityRequests++
                visible
            },
        )

        assertSame(Color4b.RED, EspModeRuntime.taggedColor(entity))
        assertTrue(EspModeRuntime.shouldBeShown(entity))

        taggedColor = Color4b.BLUE
        visible = false

        assertSame(Color4b.BLUE, EspModeRuntime.taggedColor(entity))
        assertFalse(EspModeRuntime.shouldBeShown(entity))
        assertEquals(2, colorRequests)
        assertEquals(2, visibilityRequests)
    }

    private fun livingEntityIdentity(): LivingEntity =
        unsafe.allocateInstance(ArmorStand::class.java) as LivingEntity

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }

        private val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
    }
}
