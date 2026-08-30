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

package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia.runtime

import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.entity.player.Player

internal class AmnesiaTargetResolver {

    private var cachedTargetName = ""
    private var cachedTargetWorld: Any? = null
    private var cachedTargetTick = Long.MIN_VALUE
    private var cachedTargetResolved = false
    private var cachedTarget: RemotePlayer? = null

    fun findTarget(running: Boolean, configuredName: String, localPlayer: LocalPlayer): RemotePlayer? {
        if (!running) {
            clear()
            return null
        }

        val name = configuredName.trim()
        if (name.isEmpty()) {
            clear()
            return null
        }

        val level = localPlayer.level() ?: return null
        val tick = level.gameTime
        if (isCacheFor(name, level, tick)) {
            return cachedTarget.takeIf { it?.isValidCachedTarget(name, level) == true }
        }

        cachedTargetName = name
        cachedTargetWorld = level
        cachedTargetTick = tick
        cachedTargetResolved = true
        cachedTarget = level.players().firstOrNull { it.isConfiguredTarget(name, localPlayer) } as? RemotePlayer
        return cachedTarget
    }

    fun matchesConfiguredTarget(
        running: Boolean,
        configuredName: String,
        entity: Player,
        localPlayer: LocalPlayer,
    ): Boolean = running &&
        entity is RemotePlayer &&
        entity !== localPlayer &&
        entity.gameProfile.name.equals(configuredName.trim(), ignoreCase = true)

    fun clear() {
        cachedTarget = null
        cachedTargetName = ""
        cachedTargetWorld = null
        cachedTargetTick = Long.MIN_VALUE
        cachedTargetResolved = false
    }

    private fun isCacheFor(name: String, level: Any, tick: Long): Boolean =
        cachedTargetResolved &&
            cachedTargetWorld === level &&
            cachedTargetTick == tick &&
            cachedTargetName.equals(name, ignoreCase = true)

    private fun Player.isConfiguredTarget(name: String, localPlayer: LocalPlayer): Boolean =
        this is RemotePlayer &&
            this !== localPlayer &&
            !isRemoved &&
            gameProfile.name.equals(name, ignoreCase = true) &&
            !ModuleAntiBot.isBot(this)

    private fun RemotePlayer.isValidCachedTarget(name: String, level: Any): Boolean =
        !isRemoved &&
            level() === level &&
            gameProfile.name.equals(name, ignoreCase = true)
}
