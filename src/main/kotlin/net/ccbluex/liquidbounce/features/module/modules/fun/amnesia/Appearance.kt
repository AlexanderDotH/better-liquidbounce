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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.appearance.AmnesiaDisplayNameResolver
import net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.appearance.AmnesiaSkinResolver
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.PlayerSkin

object Appearance : ToggleableValueGroup(null, "Appearance", false) {

    private val playerValue = player("Player", "")
    private val nameMcSkinValue = text("NameMCSkin", "")
    private val skinResolver = AmnesiaSkinResolver()

    internal val spoofName: String?
        get() = playerValue.get().trim().takeIf { running && it.isNotEmpty() }

    internal fun displayName(original: Component): Component? {
        val name = spoofName ?: return null
        return AmnesiaDisplayNameResolver.resolve(name, original)
    }

    internal fun skin(): PlayerSkin? {
        val name = spoofName ?: return null
        return skinResolver.resolve(name, nameMcSkinValue.get())
    }

    internal fun hasSpoofedAppearance() = spoofName != null

    internal fun parseNameMcSkinId(input: String): String? = skinResolver.parseNameMcSkinId(input)
}
