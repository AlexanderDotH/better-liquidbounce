/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.modules.render.animations.ModuleAnimations
import net.ccbluex.liquidbounce.features.module.modules.render.animations.SwingAnimations
import net.ccbluex.liquidbounce.features.module.modules.render.animations.shouldApplyOffHandTransform
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.InteractionHand
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleAnimationsTest {

    @BeforeEach
    fun bootstrap() = MinecraftBootstrap.ensureInitialized()

    @Test
    fun `enabled off-hand transforms only select off-hand render calls`() {
        assertTrue(
            shouldApplyOffHandTransform(InteractionHand.OFF_HAND, isInBothHands = false, offHandRunning = true)
        )
        assertFalse(
            shouldApplyOffHandTransform(InteractionHand.MAIN_HAND, isInBothHands = false, offHandRunning = true)
        )
        assertFalse(
            shouldApplyOffHandTransform(InteractionHand.OFF_HAND, isInBothHands = false, offHandRunning = false)
        )
    }

    @Test
    fun `enabled off-hand transform remains available to a two-handed map render`() {
        assertTrue(shouldApplyOffHandTransform(InteractionHand.MAIN_HAND, isInBothHands = true, offHandRunning = true))
    }

    @Test
    fun `fork blocking modes remain first while upstream modes and Swing Animations are additive`() {
        assertEquals(
            listOf("1.7", "Pushdown", "Sigma", "Exhibition", "Avatar", "Dortware"),
            ModuleAnimations.blockAnimationChoice.modes.map { it.name },
        )
        assertEquals("1.7", ModuleAnimations.blockAnimationChoice.activeMode.name)
        assertFalse(SwingAnimations.enabled)
    }

    @Test
    fun `swing animation mode names retain their persisted order`() {
        assertEquals(
            listOf("Swipe", "Spin", "Hook", "Dash", "Tap", "Inject", "Slap", "Akrien", "Smooth", "Power", "Feast"),
            SwingAnimations.Mode.entries.map { it.tag },
        )
    }
}
