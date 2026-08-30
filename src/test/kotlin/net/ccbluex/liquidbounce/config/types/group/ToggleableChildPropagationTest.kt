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
package net.ccbluex.liquidbounce.config.types.group

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ToggleableChildPropagationTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `parent lifecycle reaches enabled children through ordinary groups`() {
        val parent = TrackingGroup("Parent", enabled = true)
        val nested = parent.tree(ValueGroup("Nested"))
        val child = nested.tree(TrackingGroup("Child", enabled = true))

        parent.onToggled(state = false, isParentUpdate = true)
        parent.onToggled(state = true, isParentUpdate = true)

        assertEquals(1, child.disabledCalls)
        assertEquals(1, child.enabledCalls)
    }

    @Test
    fun `parent lifecycle leaves disabled child settings inactive`() {
        val parent = TrackingGroup("Parent", enabled = true)
        val child = parent.tree(TrackingGroup("Child", enabled = false))

        parent.onToggled(state = false, isParentUpdate = true)
        parent.onToggled(state = true, isParentUpdate = true)

        assertEquals(0, child.disabledCalls)
        assertEquals(0, child.enabledCalls)
    }

    @Test
    fun `group attached toggleable infers its event parent from the owning group`() {
        val owner = TrackingGroup("Owner", enabled = true)
        val child = owner.tree(TrackingGroup("Child", enabled = true))

        assertSame(owner, child.parent)
        assertSame(owner, child.parent())
    }

    @Test
    fun `explicit event parent takes precedence over the owning group`() {
        val explicitParent = object : EventListener {}
        val owner = TrackingGroup("Owner", enabled = true)
        val child = owner.tree(TrackingGroup("Child", enabled = true, parent = explicitParent))

        assertSame(explicitParent, child.parent)
        assertSame(explicitParent, child.parent())
    }

    private class TrackingGroup(
        name: String,
        enabled: Boolean,
        parent: EventListener? = null,
    ) : ToggleableValueGroup(parent, name, enabled) {
        var enabledCalls = 0
        var disabledCalls = 0

        override fun onEnabled() {
            enabledCalls++
        }

        override fun onDisabled() {
            disabledCalls++
        }
    }
}
