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
package net.ccbluex.liquidbounce.features.litematica.integration.api

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LitematicaEasyPlaceExecutionGateTest {

    @AfterEach
    fun releaseLeakedExecution() {
        LitematicaEasyPlaceExecutionGate.resetForTests()
    }

    @Test
    fun `native entry is suppressed for the complete printer ownership`() {
        assertFalse(LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace())

        LitematicaEasyPlaceExecutionGate.acquirePrinterOwnership().use {
            assertTrue(LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace())
        }

        assertFalse(LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace())
    }

    @Test
    fun `controlled bridge invocation is the only allowed entry inside owned action`() {
        LitematicaEasyPlaceExecutionGate.acquirePrinterOwnership().use { ownership ->
            assertTrue(LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace())
            ownership.beginExecution().use { token ->
                assertFalse(token.runControlled {
                    LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace()
                })
                assertTrue(LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace())
                assertFailsWith<IllegalStateException> { token.runControlled { Unit } }
            }
            assertTrue(LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace())
        }
    }

    @Test
    fun `a second action is rejected until the first token closes`() {
        LitematicaEasyPlaceExecutionGate.acquirePrinterOwnership().use { ownership ->
            ownership.beginExecution().use {
                assertFailsWith<IllegalStateException> { ownership.beginExecution() }
            }
        }
    }

    @Test
    fun `ownership acquisition and release are idempotent`() {
        val first = LitematicaEasyPlaceExecutionGate.acquirePrinterOwnership()
        val second = LitematicaEasyPlaceExecutionGate.acquirePrinterOwnership()

        assertTrue(first === second)
        first.close()
        first.close()

        assertFalse(LitematicaEasyPlaceExecutionGate.shouldSuppressNativeEasyPlace())
        assertFailsWith<IllegalStateException> {
            first.beginExecution()
        }
    }

    @Test
    fun `ownership cannot be released during a controlled action`() {
        val ownership = LitematicaEasyPlaceExecutionGate.acquirePrinterOwnership()
        ownership.beginExecution().use {
            assertFailsWith<IllegalStateException> {
                ownership.close()
            }
        }
        ownership.close()
    }
}
