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
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.features.module.modules.movement.freeze.contract.FreezeStateHook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreezeStateHookContractTest {

    @Test
    fun `neutral hook delegates the exact module running state`() {
        ModuleFreeze

        assertEquals(ModuleFreeze.running, FreezeStateHook.isRunning())
    }

}
