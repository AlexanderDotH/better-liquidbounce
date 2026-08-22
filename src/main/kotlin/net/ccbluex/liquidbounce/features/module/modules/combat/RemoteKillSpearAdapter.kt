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

package net.ccbluex.liquidbounce.features.module.modules.combat

import net.minecraft.world.entity.LivingEntity

/**
 * Spear damage is committed by the delivered terminal kinetic movement itself. The adapter records
 * that weapon-specific boundary without claiming damage before the existing evidence window does.
 */
internal object RemoteKillSpearAdapter : RemoteKillWeaponAdapter<LivingEntity> {

    override fun strike(request: RemoteKillStrikeRequest<LivingEntity>): RemoteKillStrikeResult =
        if (request.target.isAlive && !request.target.isRemoved) {
            RemoteKillStrikeResult.Committed
        } else {
            RemoteKillStrikeResult.Rejected("target-unavailable")
        }
}
