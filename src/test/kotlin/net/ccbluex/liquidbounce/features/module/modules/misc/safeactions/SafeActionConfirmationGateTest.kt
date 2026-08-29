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
package net.ccbluex.liquidbounce.features.module.modules.misc.safeactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SafeActionConfirmationGateTest {

    @Test
    fun `first fresh press arms action and requests notification`() {
        val gate = SafeActionConfirmationGate<String>()

        val decision = gate.request(action = "drop-one", freshPress = true)

        assertEquals(SafeActionConfirmationDecision.BLOCK_AND_NOTIFY, decision)
        assertEquals("drop-one", gate.pendingAction)
    }

    @Test
    fun `second fresh press for same action allows exactly once`() {
        val gate = SafeActionConfirmationGate<String>()
        gate.request(action = "drop-one", freshPress = true)

        val confirmation = gate.request(action = "drop-one", freshPress = true)
        val nextPress = gate.request(action = "drop-one", freshPress = true)

        assertEquals(SafeActionConfirmationDecision.ALLOW, confirmation)
        assertEquals(SafeActionConfirmationDecision.BLOCK_AND_NOTIFY, nextPress)
        assertEquals("drop-one", gate.pendingAction)
    }

    @Test
    fun `held or repeated input blocks without changing pending action`() {
        val gate = SafeActionConfirmationGate<String>()
        gate.request(action = "drop-one", freshPress = true)

        val decision = gate.request(action = "drop-stack", freshPress = false)

        assertEquals(SafeActionConfirmationDecision.BLOCK, decision)
        assertEquals("drop-one", gate.pendingAction)
        assertEquals(
            SafeActionConfirmationDecision.ALLOW,
            gate.request(action = "drop-one", freshPress = true),
        )
    }

    @Test
    fun `non-fresh input without pending action stays unarmed`() {
        val gate = SafeActionConfirmationGate<String>()

        val decision = gate.request(action = "drop-one", freshPress = false)

        assertEquals(SafeActionConfirmationDecision.BLOCK, decision)
        assertNull(gate.pendingAction)
    }

    @Test
    fun `different fresh action replaces pending confirmation`() {
        val gate = SafeActionConfirmationGate<String>()
        gate.request(action = "drop-one", freshPress = true)

        val decision = gate.request(action = "drop-stack", freshPress = true)

        assertEquals(SafeActionConfirmationDecision.BLOCK_AND_NOTIFY, decision)
        assertEquals("drop-stack", gate.pendingAction)
    }

    @Test
    fun `reset clears pending confirmation`() {
        val gate = SafeActionConfirmationGate<String>()
        gate.request(action = "drop-one", freshPress = true)

        gate.reset()

        assertNull(gate.pendingAction)
        assertEquals(
            SafeActionConfirmationDecision.BLOCK_AND_NOTIFY,
            gate.request(action = "drop-one", freshPress = true),
        )
    }

    @Test
    fun `matching invalidation clears pending confirmation`() {
        val gate = SafeActionConfirmationGate<String>()
        gate.request(action = "drop-one", freshPress = true)

        gate.invalidateWhen { it.startsWith("drop") }

        assertNull(gate.pendingAction)
    }

    @Test
    fun `leaving and returning to a context requires two new presses`() {
        val gate = SafeActionConfirmationGate<String>()
        gate.request(action = "context-a", freshPress = true)

        gate.invalidateWhen { pending -> pending != "context-b" }

        assertEquals(
            SafeActionConfirmationDecision.BLOCK_AND_NOTIFY,
            gate.request(action = "context-a", freshPress = true),
        )
        assertEquals(
            SafeActionConfirmationDecision.ALLOW,
            gate.request(action = "context-a", freshPress = true),
        )
    }

    @Test
    fun `non-matching invalidation preserves pending confirmation`() {
        val gate = SafeActionConfirmationGate<String>()
        gate.request(action = "drop-one", freshPress = true)

        gate.invalidateWhen { it.startsWith("throw") }

        assertEquals("drop-one", gate.pendingAction)
    }
}
