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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.contract

import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.model.VelocityMode
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmnesiaRuntimeBridgeTest {

    @Test
    fun `bridge preserves feature state decisions`() {
        val provider = object : AmnesiaRuntimeHook {
            override fun findTarget(): RemotePlayer? = null
            override fun auxiliaryVisualPosition(entity: LivingEntity, partialTicks: Float): Vec3? = null
            override fun actionContributions(entity: LivingEntity) = AmnesiaActionContributions()
            override fun visualEffects(
                entity: LivingEntity,
                partialTicks: Float,
                basePosition: Vec3,
                velocityPositionActive: Boolean,
            ) = AmnesiaVisualEffects()
            override fun delayPlayerModelRunning() = true
            override fun fakeKillAuraRunning() = false
            override fun fakeVelocityRunning() = true
            override fun fakeVelocityMode() = VelocityMode.NO_VELOCITY
            override fun clearScaffoldRenderState() = Unit
        }

        AmnesiaRuntimeBridge.withProviderForTest(provider) {
            assertTrue(AmnesiaRuntimeBridge.delayPlayerModelRunning())
            assertFalse(AmnesiaRuntimeBridge.fakeKillAuraRunning())
            assertTrue(AmnesiaRuntimeBridge.fakeVelocityRunning())
            assertEquals(VelocityMode.NO_VELOCITY, AmnesiaRuntimeBridge.fakeVelocityMode())
        }
    }
}
