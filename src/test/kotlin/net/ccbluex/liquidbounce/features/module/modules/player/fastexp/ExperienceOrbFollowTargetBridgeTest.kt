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
package net.ccbluex.liquidbounce.features.module.modules.player.fastexp

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperienceOrbFollowTargetBridgeTest {

    @Test
    fun `orb is moving to player only for identical target and greater speed`() {
        val target = Any()

        assertTrue(isFollowerMovingToTarget(target, target, 2.0, 1.0))
        assertFalse(isFollowerMovingToTarget(Any(), target, 2.0, 1.0))
        assertFalse(isFollowerMovingToTarget(target, target, 1.0, 1.0))
        assertFalse(isFollowerMovingToTarget(target, target, 0.5, 1.0))
        assertFalse(isFollowerMovingToTarget(null, target, 2.0, 1.0))
    }
}
