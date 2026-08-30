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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.config.ConfigMigrationOrder
import net.ccbluex.liquidbounce.config.ConfigMigrationRegistry
import net.ccbluex.liquidbounce.features.combat.runtime.attackEntity
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeAttacked
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.ReachHitCombatBridge
import net.ccbluex.liquidbounce.features.module.modules.player.reach.contract.ReachHitCombatPort
import net.ccbluex.liquidbounce.features.module.modules.player.reach.migrateLegacyReachConfig
import net.ccbluex.liquidbounce.features.module.modules.player.reach.hit.ReachHit
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.ReachInteractableFeature
import net.ccbluex.liquidbounce.features.range.RangeValueGroup
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.minecraft.world.entity.LivingEntity

/**
 * Reach module
 *
 * Increases your reach.
 *
 * @see net.ccbluex.liquidbounce.injection.mixins.minecraft.entity.MixinPlayer
 * @see net.ccbluex.liquidbounce.injection.mixins.minecraft.item.MixinAttackRange
 */
object ModuleReach : ClientModule("Reach", ModuleCategories.PLAYER, aliases = listOf("SuperHit")) {

    init {
        ReachHitCombatBridge.install(MinecraftReachHitCombatPort)
        ConfigMigrationRegistry.register("reach", ConfigMigrationOrder.REACH, ::migrateLegacyReachConfig)
    }

    val entity = tree(RangeValueGroup("Entity", 1f, 0f))
    val blockRangeIncrease by float("BlockRangeIncrease", 0.5f, 0f..64f)
    internal val hit = tree(ReachHit(this))
    internal val interactable = tree(ReachInteractableFeature(this))
}

private object MinecraftReachHitCombatPort : ReachHitCombatPort {
    override fun shouldAttack(entity: LivingEntity) = entity.shouldBeAttacked()

    override fun attack(entity: LivingEntity, swingMode: SwingMode, keepSprint: Boolean) {
        attackEntity(entity, swingMode, keepSprint)
    }
}
