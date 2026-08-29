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
package net.ccbluex.liquidbounce.features.litematica.integration.loader

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LitematicaOptionalClassLoadingTest {

    @Test
    fun `always loaded boundary links without optional mod classes`() {
        val loader = javaClass.classLoader

        assertTrue(runCatching { Class.forName(LITEMATICA_CLASS, false, loader) }.isFailure)
        assertNotNull(Class.forName(PORT_CLASS, false, loader))
        assertNotNull(Class.forName(LOADER_CLASS, false, loader))
        assertFalse(publicBoundaryTypeNames().any { it.startsWith("fi.dy.masa.") })
    }

    private fun publicBoundaryTypeNames(): Sequence<String> = sequence {
        for (method in LitematicaPort::class.java.methods) {
            yield(method.returnType.name)
            yieldAll(method.parameterTypes.asSequence().map { it.name })
        }
    }

    private companion object {
        const val LITEMATICA_CLASS = "fi.dy.masa.litematica.Litematica"
        const val PORT_CLASS =
            "net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort"
        const val LOADER_CLASS =
            "net.ccbluex.liquidbounce.features.litematica.integration.loader.LitematicaPortLoader"
    }
}
