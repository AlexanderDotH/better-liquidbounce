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
package net.ccbluex.liquidbounce.integration.theme.component

import com.mojang.datafixers.util.Pair
import net.minecraft.client.gui.Hud
import net.minecraft.client.gui.contextualbar.ContextualBar

interface HudContextualInfoAccess {
    val contextualInfoBar: Pair<Hud.ContextualInfo, ContextualBar>
}
