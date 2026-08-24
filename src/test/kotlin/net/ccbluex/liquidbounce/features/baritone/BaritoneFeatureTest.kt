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
package net.ccbluex.liquidbounce.features.baritone

import net.ccbluex.liquidbounce.features.baritone.core.BaritoneFacade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class BaritoneFeatureTest {

    @Test
    fun `uninstall only removes the facade that currently owns the runtime`() {
        val owner = facadeProxy()
        val staleOwner = facadeProxy()

        BaritoneFeature.install(owner)
        BaritoneFeature.uninstall(staleOwner)
        assertSame(owner, BaritoneFeature.facadeOrNull())

        BaritoneFeature.uninstall(owner)
        assertNull(BaritoneFeature.facadeOrNull())
    }

    @Test
    fun `dashboard action uses the installed screen opener`() {
        var opened = 0
        BaritoneFeature.useDashboardOpener { opened++ }

        try {
            BaritoneFeature.openDashboard()
            assertEquals(1, opened)
        } finally {
            BaritoneFeature.restoreDashboardOpener()
        }
    }

    private fun facadeProxy() = Proxy.newProxyInstance(
        BaritoneFacade::class.java.classLoader,
        arrayOf(BaritoneFacade::class.java),
    ) { _, _, _ -> null } as BaritoneFacade
}
