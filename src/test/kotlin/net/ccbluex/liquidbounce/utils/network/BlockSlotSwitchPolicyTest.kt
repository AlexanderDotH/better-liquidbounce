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
package net.ccbluex.liquidbounce.utils.network

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockSlotSwitchPolicyTest {

    @Test
    fun `require-selected rejects a different slot and never restores it`() {
        val policy = BlockSlotSwitchPolicy.REQUIRE_SELECTED

        assertTrue(policy.canUseSlot(slotChanged = false))
        assertFalse(policy.canUseSlot(slotChanged = true))
        assertFalse(policy.shouldRestoreServerSlot(slotChanged = true, mainHand = true))
    }

    @Test
    fun `restore-after-use accepts a different main-hand slot and restores only that slot`() {
        val policy = BlockSlotSwitchPolicy.RESTORE_AFTER_USE

        assertTrue(policy.canUseSlot(slotChanged = true))
        assertTrue(policy.shouldRestoreServerSlot(slotChanged = true, mainHand = true))
        assertFalse(policy.shouldRestoreServerSlot(slotChanged = false, mainHand = true))
        assertFalse(policy.shouldRestoreServerSlot(slotChanged = true, mainHand = false))
    }

    @Test
    fun `keep-selected accepts a different slot without a restore packet`() {
        val policy = BlockSlotSwitchPolicy.KEEP_SELECTED

        assertTrue(policy.canUseSlot(slotChanged = true))
        assertFalse(policy.shouldRestoreServerSlot(slotChanged = true, mainHand = true))
    }
}
