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
package net.ccbluex.liquidbounce.features.baritone.adapter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaritoneAutomationActivationTest {

    @Test
    fun `successful task activation enables the owning module`() {
        var activations = 0
        val activation = BaritoneAutomationActivation { activations++ }

        assertEquals("started", activation.afterSuccess { "started" })
        assertEquals(1, activations)
    }

    @Test
    fun `failed task and rejected command do not enable the module`() {
        var activated = false
        val activation = BaritoneAutomationActivation { activated = true }

        assertFailsWith<IllegalStateException> { activation.afterSuccess { error("rejected") } }
        assertFalse(activation.accepted(false))
        assertFalse(activated)
        assertTrue(activation.accepted(true))
        assertTrue(activated)
    }

    @Test
    fun `observed upstream path start activates the owning module`() {
        var activations = 0
        val activation = BaritoneAutomationActivation { activations++ }

        activation.observedPathStart()

        assertEquals(1, activations)
    }
}
