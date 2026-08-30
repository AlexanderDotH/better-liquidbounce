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
package net.ccbluex.liquidbounce.features.block.contract

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.ccbluex.liquidbounce.utils.aiming.RotationTarget
import net.ccbluex.liquidbounce.utils.aiming.RotationTargetFactory
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.utils.client.RestrictedSingleUseAction
import net.minecraft.world.entity.Entity
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class BlockPlacementRotationBridgeTest {

    @Test
    fun `installed provider creates settings and preserves scheduling arguments`() {
        MinecraftBootstrap.ensureInitialized()

        val settingsOwner = TestOwner()
        val taskOwner = TestOwner()
        val task = Runnable { }
        val settings = BlockPlacementRotationSettings(
            ValueGroup("Rotations"),
            UnusedRotationTargetFactory,
        )
        val provider = RecordingProvider(settings)

        BlockPlacementRotationBridge.withProviderForTest(null) {
            BlockPlacementRotationBridge.install(provider)

            assertSame(settings, BlockPlacementRotationBridge.createSettings(settingsOwner))
            BlockPlacementRotationBridge.schedule(taskOwner, postMove = true, priority = true, task = task)
        }

        assertSame(settingsOwner, provider.settingsOwner)
        assertSame(taskOwner, provider.taskOwner)
        assertSame(task, provider.task)
    }

    @Test
    fun `missing provider fails instead of silently dropping rotation work`() {
        BlockPlacementRotationBridge.withProviderForTest(null) {
            assertFailsWith<IllegalStateException> {
                BlockPlacementRotationBridge.createSettings(TestOwner())
            }
        }
    }

    private class RecordingProvider(
        private val settings: BlockPlacementRotationSettings,
    ) : BlockPlacementRotationProvider {
        var settingsOwner: EventListener? = null
        var taskOwner: EventListener? = null
        var task: Runnable? = null

        override fun createSettings(owner: EventListener): BlockPlacementRotationSettings {
            settingsOwner = owner
            return settings
        }

        override fun schedule(owner: EventListener, postMove: Boolean, priority: Boolean, task: Runnable) {
            check(postMove)
            check(priority)
            taskOwner = owner
            this.task = task
        }
    }

    private class TestOwner : EventListener

    private object UnusedRotationTargetFactory : RotationTargetFactory {
        override fun toRotationTarget(
            rotation: Rotation,
            entity: Entity?,
            considerInventory: Boolean,
            whenReached: RestrictedSingleUseAction?,
        ): RotationTarget = error("The bridge contract test does not create rotation targets")
    }
}
