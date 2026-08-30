/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.trigger

import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.trigger.triggers.ClientBlockBreakTrigger

/** Stable Java boundary for the client-side block-break trigger. */
object ClientBlockBreakHook {

    @JvmStatic
    fun onClientBlockBreak() {
        dispatchClientBlockBreak(ClientBlockBreakTrigger::clientBreakHandler)
    }
}

internal inline fun dispatchClientBlockBreak(handler: () -> Unit) {
    handler()
}
