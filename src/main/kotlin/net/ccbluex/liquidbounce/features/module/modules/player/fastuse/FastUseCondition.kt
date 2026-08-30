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
package net.ccbluex.liquidbounce.features.module.modules.player.fastuse

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.entity.moving
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects

internal enum class FastUseCondition(override val tag: String) : Tagged {
    NOT_IN_THE_AIR("NotInTheAir") {
        override fun meets(player: LocalPlayer) = !player.onGround()
    },
    NOT_DURING_MOVE("NotDuringMove") {
        override fun meets(player: LocalPlayer) = player.moving
    },
    NOT_DURING_REGENERATION("NotDuringRegeneration") {
        override fun meets(player: LocalPlayer) = player.hasEffect(MobEffects.REGENERATION)
    };

    abstract fun meets(player: LocalPlayer): Boolean
}
