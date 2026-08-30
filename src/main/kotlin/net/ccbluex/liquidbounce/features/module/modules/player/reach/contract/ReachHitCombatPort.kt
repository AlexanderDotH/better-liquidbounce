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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.contract

import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.minecraft.world.entity.LivingEntity

internal interface ReachHitCombatPort {
    fun shouldAttack(entity: LivingEntity): Boolean
    fun attack(entity: LivingEntity, swingMode: SwingMode, keepSprint: Boolean)
}

internal object ReachHitCombatBridge : ReachHitCombatPort {
    private object DisabledCombat : ReachHitCombatPort {
        override fun shouldAttack(entity: LivingEntity) = false
        override fun attack(entity: LivingEntity, swingMode: SwingMode, keepSprint: Boolean) = Unit
    }

    private var provider: ReachHitCombatPort = DisabledCombat

    fun install(provider: ReachHitCombatPort) {
        this.provider = provider
    }

    override fun shouldAttack(entity: LivingEntity) = provider.shouldAttack(entity)

    override fun attack(entity: LivingEntity, swingMode: SwingMode, keepSprint: Boolean) {
        provider.attack(entity, swingMode, keepSprint)
    }
}
