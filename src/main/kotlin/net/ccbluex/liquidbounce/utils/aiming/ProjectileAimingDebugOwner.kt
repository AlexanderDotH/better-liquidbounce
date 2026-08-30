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

package net.ccbluex.liquidbounce.utils.aiming

import net.ccbluex.liquidbounce.common.debug.DebuggedOwner
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.ccbluex.liquidbounce.utils.text.plus
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

internal object ProjectileAimingDebugOwner : DebuggedOwner {
    override val debugDisplayName: Component = "ProjectileAimbot".asPlainText(
        Style.EMPTY + ChatFormatting.GOLD + ChatFormatting.BOLD,
    )

    override val debugOwnerId: String = "ModuleProjectileAimbot"
}
