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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.collection.Filter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ReachInteractableConfigurationTest {

    @Test
    fun `Interactable exposes the planned safe defaults`() {
        val feature = ReachInteractableFeature(TestParent)

        assertFalse(feature.enabled)
        feature.setting("MaxRange").assertRanged(256f, 8f, 512f)
        feature.setting("InteractionRange").assertRanged(4.5f, 3f, 6f)
        assertEquals(Filter.BLACKLIST, feature.setting("Filter").get())
        assertTrue((feature.setting("Blocks").get() as Set<*>).isEmpty())
        assertEquals(true, feature.setting("ContainerVehicles").get())

        val routing = feature.group("Routing")
        assertEquals(4096, routing.setting("MaxCost").get())
        assertEquals(true, routing.setting("Diagonal").get())
        assertEquals(true, routing.setting("LineOfSightShortcuts").get())
        assertEquals(9.5f, routing.setting("StepDistance").get())
        assertEquals(0, routing.setting("StepDelay").get())
        assertEquals(750, routing.setting("NodesPerTick").get())
        assertEquals(true, routing.setting("RenderPath").get())

        assertEquals(2, feature.setting("OpenRetries").get())
        assertEquals(20, feature.setting("OpenTimeout").get())
        assertEquals(400, feature.setting("RouteTimeout").get())
        assertEquals(0, feature.setting("HoldTimeout").get())
    }

    @Test
    fun `surface fallback defaults to protected Vanilla transport and exposes Folia`() {
        val surfaceFallback = ReachInteractableFeature(TestParent).group("SurfaceFallback")
            as ToggleableValueGroup

        assertTrue(surfaceFallback.enabled)
        assertEquals(128, surfaceFallback.setting("MaxRise").get())
        assertEquals(48, surfaceFallback.setting("HorizontalSearch").get())
        assertEquals(true, surfaceFallback.setting("DoNotClipAroundBedrock").get())

        val vClip = surfaceFallback.group("VClip") as ModeValueGroup<*>
        assertEquals(listOf("Vanilla", "Folia"), vClip.modes.map { it.name })
        assertEquals("Vanilla", vClip.activeMode.name)
        assertEquals(false, vClip.mode("Vanilla").setting("PaperBypass").get())
        assertEquals(false, vClip.mode("Vanilla").setting("FullPacket").get())
        assertEquals(5, vClip.mode("Folia").setting("MovementPackets").get())
        assertEquals(false, vClip.mode("Folia").setting("FullPacket").get())
        assertTrue(vClip.modes.none { mode -> mode.inner.any { it.name == "ResetMotion" } })
    }

    private companion object {
        object TestParent : EventListener

        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraft() {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}

private fun ValueGroup.setting(name: String): Value<*> = inner.single { it.name == name }

private fun ValueGroup.group(name: String): ValueGroup = inner.single { it.name == name } as ValueGroup

private fun ModeValueGroup<*>.mode(name: String): ValueGroup = modes.single { it.name == name }

private fun Value<*>.assertRanged(default: Number, minimum: Number, maximum: Number) {
    this as RangedValue<*>
    assertEquals(default.toDouble(), (get() as Number).toDouble(), 0.0)
    assertEquals(minimum.toDouble(), (range.start as Number).toDouble(), 0.0)
    assertEquals(maximum.toDouble(), (range.endInclusive as Number).toDouble(), 0.0)
}
