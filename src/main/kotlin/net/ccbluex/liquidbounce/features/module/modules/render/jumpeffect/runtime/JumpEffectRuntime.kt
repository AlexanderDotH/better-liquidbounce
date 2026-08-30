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
package net.ccbluex.liquidbounce.features.module.modules.render.jumpeffect.runtime

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.collection.ExpiringList.Companion.ExpiringList
import net.minecraft.world.phys.Vec3

internal class JumpEffectRuntime(owner: EventListener) {
    val circles = owner.ExpiringList<Vec3>()
}
