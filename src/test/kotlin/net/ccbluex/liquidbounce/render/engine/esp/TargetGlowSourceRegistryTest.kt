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
package net.ccbluex.liquidbounce.render.engine.esp

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import sun.misc.Unsafe

class TargetGlowSourceRegistryTest {

    private val registrations = mutableListOf<AutoCloseable>()

    @AfterEach
    fun clearRegistry() {
        registrations.asReversed().forEach(AutoCloseable::close)
        TargetGlowSourceRegistry.beginFrame()
    }

    @Test
    fun `first active source owns target color and contributes its style once`() {
        val target = entityIdentity()
        val first = TargetGlowSelection(target, Color4b.RED, EspGlowStyle.DEFAULT.copy(radius = 18f))
        val second = TargetGlowSelection(target, Color4b.BLUE, EspGlowStyle.DEFAULT.copy(radius = 24f))
        register { first }
        register { second }

        val resolved = TargetGlowSourceRegistry.selectionFor(target)
        TargetGlowSourceRegistry.selectionFor(target)

        assertSame(first, resolved)
        assertEquals(listOf(first.style), TargetGlowSourceRegistry.consumeContributedStyles())
        assertEquals(emptyList<EspGlowStyle>(), TargetGlowSourceRegistry.consumeContributedStyles())
    }

    @Test
    fun `inactive and nonmatching sources neither select nor contribute`() {
        val requested = entityIdentity()
        val other = entityIdentity()
        register { null }
        register {
            TargetGlowSelection(other, Color4b.GREEN, EspGlowStyle.DEFAULT)
        }

        assertNull(TargetGlowSourceRegistry.selectionFor(requested))
        assertEquals(emptyList<EspGlowStyle>(), TargetGlowSourceRegistry.consumeContributedStyles())
    }

    @Test
    fun `begin frame discards styles that were not requested again`() {
        val target = entityIdentity()
        register {
            TargetGlowSelection(target, Color4b.RED, EspGlowStyle.DEFAULT.copy(intensity = 1.5f))
        }
        TargetGlowSourceRegistry.selectionFor(target)

        TargetGlowSourceRegistry.beginFrame()

        assertEquals(emptyList<EspGlowStyle>(), TargetGlowSourceRegistry.consumeContributedStyles())
    }

    @Test
    fun `matched target style merges with the shared glow lane through the strongest values`() {
        val target = entityIdentity()
        val targetStyle = EspGlowStyle.DEFAULT.copy(radius = 22f, intensity = 0.5f)
        val generalStyle = EspGlowStyle.DEFAULT.copy(radius = 8f, intensity = 1.7f)
        register { TargetGlowSelection(target, Color4b.RED, targetStyle) }
        TargetGlowSourceRegistry.selectionFor(target)

        val resolved = EspShaderStyleResolver.resolveGlow(
            generalStyle,
            *TargetGlowSourceRegistry.consumeContributedStyles().toTypedArray(),
        )

        assertEquals(22f, resolved.radius)
        assertEquals(1.7f, resolved.intensity)
    }

    private fun register(source: () -> TargetGlowSelection?) {
        registrations += TargetGlowSourceRegistry.register(source)
    }

    private fun entityIdentity(): Entity = unsafe.allocateInstance(ArmorStand::class.java) as Entity

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
