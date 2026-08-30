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
package net.ccbluex.liquidbounce.features.module.modules.`fun`.amnesia

import net.ccbluex.liquidbounce.features.module.modules.`fun`.ModuleAmnesia
import net.ccbluex.liquidbounce.render.playermodel.AmnesiaPlayerModelBridge
import net.ccbluex.liquidbounce.render.playermodel.AmnesiaPlayerModelProvider
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

internal object AmnesiaPlayerModelAdapter : AmnesiaPlayerModelProvider {

    fun install() {
        AmnesiaPlayerModelBridge.install(this)
    }

    override fun isRunning() = ModuleAmnesia.running
    override fun findTarget() = ModuleAmnesia.findTarget()
    override fun isTarget(entity: LivingEntity) = ModuleAmnesia.isAmnesiaTarget(entity)
    override fun shouldFakeSneak(entity: LivingEntity) = ModuleAmnesia.shouldFakeSneak(entity)
    override fun actionState(entity: LivingEntity) = ModuleAmnesia.getActionState(entity)
    override fun spoofedName(player: Player) = ModuleAmnesia.getSpoofedName(player)
    override fun spoofedDisplayName(player: Player, original: Component) =
        ModuleAmnesia.getSpoofedDisplayName(player, original)
    override fun spoofedSkin(player: AbstractClientPlayer) = ModuleAmnesia.getSpoofedSkin(player)
    override fun visualTransform(entity: LivingEntity) = ModuleAmnesia.getVisualTransform(entity)
}
