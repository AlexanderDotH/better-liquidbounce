/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 * Copyright (c) 2015 - 2026 CCBlueX
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.noslow

import net.ccbluex.liquidbounce.features.module.modules.movement.noslow.modes.powdersnow.NoSlowPowderSnow
import net.ccbluex.liquidbounce.features.module.modules.movement.noslow.modes.slime.NoSlowSlime
import net.minecraft.world.entity.Entity

object NoSlowInjectionHook {
    @JvmStatic fun isSlimeEnabled() = NoSlowSlime.running

    @JvmStatic
    fun applyPowderSnowVelocity(entity: Entity): Boolean {
        if (!NoSlowPowderSnow.running) return false
        val velocity = entity.deltaMovement
        val multiplier = NoSlowPowderSnow.multiplier
        entity.setDeltaMovement(velocity.x * multiplier, velocity.y, velocity.z * multiplier)
        return true
    }
}
