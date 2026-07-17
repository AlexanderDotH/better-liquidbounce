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

package net.ccbluex.liquidbounce.utils.render

import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.PlayerSkin

object PlayerModelAppearanceHook {

    private val resolvingNameSpoof = ThreadLocal.withInitial { false }

    @JvmStatic
    fun replaceName(player: Player, original: Component): Component {
        return withoutRecursiveNameSpoof(original) {
            ModuleAmnesia.getSpoofedDisplayName(player, original) ?: original
        }
    }

    @JvmStatic
    fun replacePlainName(player: Player, original: String): String =
        withoutRecursiveNameSpoof(original) {
            ModuleAmnesia.getSpoofedName(player) ?: original
        }

    @JvmStatic
    fun replacePlayerInfoName(playerInfo: PlayerInfo, original: Component): Component {
        val player = mc.level?.getPlayerByUUID(playerInfo.profile.id) as? Player ?: return original
        return replaceName(player, original)
    }

    @JvmStatic
    fun replaceSkin(player: AbstractClientPlayer, original: PlayerSkin): PlayerSkin =
        ModuleAmnesia.getSpoofedSkin(player) ?: original

    @JvmStatic
    fun applyAppearance(entity: LivingEntity, state: LivingEntityRenderState) {
        val nameTag = state.nameTag
        if (entity is Player && nameTag != null) {
            state.nameTag = replaceName(entity, nameTag)
        }

        if (entity is AbstractClientPlayer && state is AvatarRenderState) {
            state.skin = replaceSkin(entity, state.skin)
        }
    }

    private inline fun <T> withoutRecursiveNameSpoof(original: T, replacement: () -> T): T {
        if (resolvingNameSpoof.get()) {
            return original
        }

        resolvingNameSpoof.set(true)
        return try {
            replacement()
        } finally {
            resolvingNameSpoof.set(false)
        }
    }
}
