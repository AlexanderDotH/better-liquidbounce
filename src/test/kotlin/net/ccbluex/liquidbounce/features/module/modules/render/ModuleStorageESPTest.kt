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
package net.ccbluex.liquidbounce.features.module.modules.render

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ModuleStorageESPTest {

    @Test
    fun `StorageESP declares a distinct minecart container category`() {
        val minecartCategory = ModuleStorageESP.ChestType::class.java.declaredClasses
            .singleOrNull { it.simpleName == "Minecart" }

        assertNotNull(minecartCategory, "Minecart containers need their own configurable category")
    }

    @Test
    fun `StorageESP schema does not expose a ChestStealer dependency`() {
        val declaresChestStealerSetting = ModuleStorageESP::class.java.declaredFields.any { field ->
            field.name.startsWith("requiresChestStealer")
        }

        assertFalse(declaresChestStealerSetting)
    }

    @Test
    fun `StorageESP uses the standard module running state`() {
        val overridesRunning = ModuleStorageESP::class.java.declaredMethods.any { method ->
            method.name == "getRunning"
        }

        assertFalse(overridesRunning, "StorageESP must remain active independently of ChestStealer")
    }
}
