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
package net.ccbluex.liquidbounce.features.module.modules.render.potionfx

import net.ccbluex.liquidbounce.features.module.modules.render.potionfx.contract.ParticleColorBridge
import net.ccbluex.liquidbounce.features.module.modules.render.potionfx.contract.ParticleColorHook
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.core.particles.ParticleTypes
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ParticleColorBridgeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() = MinecraftBootstrap.ensureInitialized()
    }

    @Test
    fun `installed provider preserves packed particle color`() {
        ParticleColorBridge.withProviderForTest(ParticleColorHook { 0x12345678 }) {
            assertEquals(0x12345678, ParticleColorBridge.color(ParticleTypes.FLAME))
        }
    }
}
