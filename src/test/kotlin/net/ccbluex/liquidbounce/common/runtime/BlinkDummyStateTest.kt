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
package net.ccbluex.liquidbounce.common.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlinkDummyStateTest {

    @Test
    fun `uninstalled state does not classify players as blink dummies`() {
        BlinkDummyState.withProviderForTest(null) {
            assertFalse(BlinkDummyState.isDummyPlayer(42))
        }
    }

    @Test
    fun `provider receives the unchanged entity id and owns the decision`() {
        val dummyId = Int.MIN_VALUE + 17
        val observedIds = mutableListOf<Int>()
        val provider = BlinkDummyStateProvider { entityId ->
            observedIds += entityId
            entityId == dummyId
        }

        BlinkDummyState.withProviderForTest(provider) {
            assertTrue(BlinkDummyState.isDummyPlayer(dummyId))
            assertFalse(BlinkDummyState.isDummyPlayer(dummyId + 1))
        }

        assertEquals(listOf(dummyId, dummyId + 1), observedIds)
    }
}
