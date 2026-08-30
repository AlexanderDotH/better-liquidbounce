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
package net.ccbluex.liquidbounce.features.module.modules.movement.targetstrafe.planner

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TargetStrafeMotionPolicyTest {

    @Test
    fun `hypixel speed floor applies only while speed is running`() {
        assertEquals(0.2, targetStrafeMotionSpeed(0.2, hypixel = false, speedRunning = true, onGround = true))
        assertEquals(0.2, targetStrafeMotionSpeed(0.2, hypixel = true, speedRunning = false, onGround = true))
        assertEquals(0.48, targetStrafeMotionSpeed(0.2, hypixel = true, speedRunning = true, onGround = true))
        assertEquals(0.281, targetStrafeMotionSpeed(0.2, hypixel = true, speedRunning = true, onGround = false))
        assertEquals(0.6, targetStrafeMotionSpeed(0.6, hypixel = true, speedRunning = true, onGround = false))
    }

    @Test
    fun `hypixel low hop owns strafe strength only while speed is running`() {
        assertEquals(1.0, targetStrafeStrength(hypixel = false, speedRunning = true, lowHopShouldStrafe = false))
        assertEquals(1.0, targetStrafeStrength(hypixel = true, speedRunning = false, lowHopShouldStrafe = false))
        assertEquals(0.02, targetStrafeStrength(hypixel = true, speedRunning = true, lowHopShouldStrafe = false))
        assertEquals(1.0, targetStrafeStrength(hypixel = true, speedRunning = true, lowHopShouldStrafe = true))
    }
}
