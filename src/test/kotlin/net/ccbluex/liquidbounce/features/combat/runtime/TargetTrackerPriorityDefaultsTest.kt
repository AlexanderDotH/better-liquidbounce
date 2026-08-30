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
package net.ccbluex.liquidbounce.features.combat.runtime

import net.ccbluex.fastutil.objectLinkedSetOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TargetTrackerPriorityDefaultsTest {

    @Test
    fun `explicit distance-only defaults do not prepend player type`() {
        val configured = targetTrackerDefaultPriorities(
            defaultPriority = TargetPriority.HEALTH,
            explicitPriorities = objectLinkedSetOf(TargetPriority.DISTANCE),
        )

        assertEquals(listOf(TargetPriority.DISTANCE), configured.toList())
    }

    @Test
    fun `existing default retains player type before health`() {
        val configured = targetTrackerDefaultPriorities(TargetPriority.HEALTH)

        assertEquals(listOf(TargetPriority.TYPE, TargetPriority.HEALTH), configured.toList())
    }
}
