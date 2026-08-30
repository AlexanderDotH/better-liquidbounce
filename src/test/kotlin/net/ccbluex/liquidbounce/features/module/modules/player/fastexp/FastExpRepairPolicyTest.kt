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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FastExpRepairPolicyTest {

    @Test
    fun `bottle count preserves repair rate truncation and available stack cap`() {
        assertEquals(10, requiredExperienceBottleCount(totalDamage = 140, availableBottles = 64))
        assertEquals(0, requiredExperienceBottleCount(totalDamage = 13, availableBottles = 64))
        assertEquals(1, requiredExperienceBottleCount(totalDamage = 28, availableBottles = 1))
    }
}
