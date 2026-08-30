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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.appearance

import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component

internal object AmnesiaDisplayNameResolver {

    fun resolve(name: String, original: Component): Component {
        findOnlineDisplayName(name)?.let { return it }
        return name.asPlainText(original.style)
    }

    private fun findOnlineDisplayName(name: String): Component? {
        val playerInfo = findOnlinePlayerInfo(name)
        playerInfo?.tabListDisplayName?.let { return it }
        playerInfo?.team?.let { return it.getFormattedName(name.asPlainText()) }

        val onlinePlayer = mc.level?.players()
            ?.firstOrNull { it.gameProfile.name.equals(name, ignoreCase = true) }
            ?: return null
        val styledName = name.asPlainText(onlinePlayer.name.style)
        return onlinePlayer.team?.getFormattedName(styledName) ?: styledName
    }

    private fun findOnlinePlayerInfo(name: String): PlayerInfo? =
        mc.connection?.onlinePlayers?.firstOrNull {
            it.profile.name.equals(name, ignoreCase = true)
        }
}
