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
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationBridge
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationProvider
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationSettings
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.RotationTargetFactory
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.minecraft.world.entity.Entity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlockPlacerContractTest {

    @Test
    fun `constructor settings and rotation modes keep their public surface`() =
        BlockPlacementRotationBridge.withProviderForTest(TestRotationProvider) {
            assertPublicSurface()
        }

    private fun assertPublicSurface() {
        val module = ClientModule("BlockPlacerContract", ModuleCategories.WORLD)
        val placer = BlockPlacer("Place", module, Priority.NORMAL, { null })

        assertEquals("Place", placer.name)
        assertEquals(module, placer.module)
        assertEquals(Priority.NORMAL, placer.priority)
        assertEquals(
            listOf(
                "Range", "WallRange", "Cooldown", "Swing", "ConstructFailResult", "Sneak", "Ignore",
                "SlotResetDelay", "RotationMode", "Support", "DestroyCrystals", "TargetRendering", "PlacedRendering",
            ),
            placer.inner.map { it.name },
        )
        val rotations = placer.inner.filterIsInstance<ModeValueGroup<*>>().single { it.name == "RotationMode" }
        assertEquals("Normal", rotations.activeMode.name)
        assertEquals(listOf("Normal", "None"), rotations.modes.map { it.name })

        val crystalDestroyer = placer.inner.filterIsInstance<ToggleableValueGroup>()
            .single { it.name == "DestroyCrystals" }
        assertEquals(
            listOf("Enabled", "Range", "WallRange", "Delay", "SwingMode", "RotationMode"),
            crystalDestroyer.inner.map { it.name },
        )
        val crystalRotations = crystalDestroyer.inner.filterIsInstance<ModeValueGroup<*>>().single()
        assertEquals("Normal", crystalRotations.activeMode.name)
        assertEquals(listOf("Normal", "None"), crystalRotations.modes.map { it.name })
        assertEquals(
            listOf("PostMove", "Instant", "Rotations", "IgnoreOpenInventory"),
            crystalRotations.modes[0].inner.map { it.name },
        )
        assertEquals(
            listOf("PostMove", "Instant", "SendRotationPacket"),
            crystalRotations.modes[1].inner.map { it.name },
        )
    }

    private object TestRotationProvider : BlockPlacementRotationProvider {
        override fun createSettings(owner: EventListener) = BlockPlacementRotationSettings(
            valueGroup = ValueGroup("Rotations"),
            targetFactory = UnusedRotationTargetFactory,
        )

        override fun schedule(
            owner: EventListener,
            postMove: Boolean,
            priority: Boolean,
            task: Runnable,
        ): Unit = error("The constructor contract must not schedule rotation work")
    }

    private object UnusedRotationTargetFactory : RotationTargetFactory {
        override fun toRotationTarget(
            rotation: Rotation,
            entity: Entity?,
            considerInventory: Boolean,
            whenReached: RestrictedSingleUseAction?,
        ): RotationTarget = error("The constructor contract must not create rotation targets")
    }

    private companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }
}
