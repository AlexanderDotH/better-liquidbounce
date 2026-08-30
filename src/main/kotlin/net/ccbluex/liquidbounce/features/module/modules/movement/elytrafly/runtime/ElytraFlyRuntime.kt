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
package net.ccbluex.liquidbounce.features.module.modules.movement.elytrafly.runtime

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Items

internal object ElytraFlyRuntime {
    private lateinit var speedEnabledProvider: () -> Boolean
    private lateinit var horizontalSpeedProvider: () -> Float
    private lateinit var verticalSpeedProvider: () -> Float
    private lateinit var messageProvider: (String, Array<out Any>) -> Component

    val speedEnabled: Boolean
        get() = speedEnabledProvider()
    val horizontalSpeed: Float
        get() = horizontalSpeedProvider()
    val verticalSpeed: Float
        get() = verticalSpeedProvider()

    fun bind(
        speedEnabledProvider: () -> Boolean,
        horizontalSpeedProvider: () -> Float,
        verticalSpeedProvider: () -> Float,
        messageProvider: (String, Array<out Any>) -> Component,
    ) {
        this.speedEnabledProvider = speedEnabledProvider
        this.horizontalSpeedProvider = horizontalSpeedProvider
        this.verticalSpeedProvider = verticalSpeedProvider
        this.messageProvider = messageProvider
    }

    fun message(key: String, vararg arguments: Any): Component = messageProvider(key, arguments)
}

internal fun shouldNotOperateElytraFly(player: LocalPlayer): Boolean {
    if (player.vehicle != null) return true
    if (player.abilities.instabuild || player.hasEffect(MobEffects.LEVITATION)) return true
    val chestSlot = player.getItemBySlot(EquipmentSlot.CHEST)
    return chestSlot.item != Items.ELYTRA || chestSlot.nextDamageWillBreak()
}
