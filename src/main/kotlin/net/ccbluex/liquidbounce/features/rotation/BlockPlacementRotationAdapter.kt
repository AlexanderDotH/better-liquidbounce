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

package net.ccbluex.liquidbounce.features.rotation

import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationBridge
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationProvider
import net.ccbluex.liquidbounce.features.block.contract.BlockPlacementRotationSettings
import net.ccbluex.liquidbounce.features.module.ClientModule

object BlockPlacementRotationAdapter : BlockPlacementRotationProvider {

    fun install() = BlockPlacementRotationBridge.install(this)

    override fun createSettings(owner: EventListener): BlockPlacementRotationSettings {
        val rotations = RotationsValueGroup(owner)
        return BlockPlacementRotationSettings(
            valueGroup = rotations,
            targetFactory = rotations,
        )
    }

    override fun schedule(
        owner: EventListener,
        postMove: Boolean,
        priority: Boolean,
        task: Runnable,
    ) {
        val module = requireNotNull(owner as? ClientModule) {
            "Block placement rotation tasks must be owned by a ClientModule"
        }
        PostRotationExecutor.addTask(module, postMove, priority, task)
    }
}
