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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation

import net.ccbluex.liquidbounce.utils.entity.withStrafe
import net.ccbluex.liquidbounce.utils.movement.DirectionalInput
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlyAutomationInputTest {

    @Test
    fun `world-space steering resolves relative to the current view`() {
        val intent = FlySteeringIntent(Vec3(1.0, 0.0, 0.0))

        val resolved = FlyAutomationInputResolver.directional(
            intent = intent,
            physical = DirectionalInput.NONE,
            playerYaw = 0f,
        )

        assertEquals(DirectionalInput.LEFT, resolved)
        assertEquals(-90f, FlyAutomationInputResolver.desiredYaw(intent, DirectionalInput.NONE))
    }

    @Test
    fun `resolved input and world yaw produce the requested velocity without a second rotation`() {
        val intent = FlySteeringIntent(Vec3(1.0, 0.0, 0.0))
        val input = FlyAutomationInputResolver.directional(intent, DirectionalInput.NONE, playerYaw = 0f)
        val yaw = FlyAutomationInputResolver.desiredYaw(intent, DirectionalInput.NONE)

        val movement = Vec3.ZERO.withStrafe(speed = 1.0, input = input, yaw = requireNotNull(yaw))

        assertEquals(1.0, movement.x, 1.0e-9)
        assertEquals(0.0, movement.z, 1.0e-9)
    }

    @Test
    fun `physical directional input has precedence over automation`() {
        val intent = FlySteeringIntent(Vec3(0.0, 0.0, 1.0))

        val resolved = FlyAutomationInputResolver.directional(
            intent = intent,
            physical = DirectionalInput.RIGHT,
            playerYaw = 0f,
        )

        assertEquals(DirectionalInput.RIGHT, resolved)
        assertNull(FlyAutomationInputResolver.desiredYaw(intent, DirectionalInput.RIGHT))
    }

    @Test
    fun `automation supplies vertical input only while the user is idle`() {
        val ascending = FlySteeringIntent(Vec3(0.0, 2.0, 0.0))
        val descending = FlySteeringIntent(Vec3(0.0, -2.0, 0.0))

        assertTrue(FlyAutomationInputResolver.jump(ascending, physical = false))
        assertFalse(FlyAutomationInputResolver.sneak(ascending, physical = false))
        assertFalse(FlyAutomationInputResolver.jump(descending, physical = false))
        assertTrue(FlyAutomationInputResolver.sneak(descending, physical = false))
        assertTrue(FlyAutomationInputResolver.jump(descending, physical = true))
        assertTrue(FlyAutomationInputResolver.sneak(ascending, physical = true))
    }

    @Test
    fun `zero steering intent resolves to neutral movement`() {
        val intent = FlySteeringIntent(Vec3.ZERO)

        assertEquals(
            DirectionalInput.NONE,
            FlyAutomationInputResolver.directional(intent, DirectionalInput.NONE, playerYaw = 42f),
        )
        assertNull(FlyAutomationInputResolver.desiredYaw(intent, DirectionalInput.NONE))
    }
}
