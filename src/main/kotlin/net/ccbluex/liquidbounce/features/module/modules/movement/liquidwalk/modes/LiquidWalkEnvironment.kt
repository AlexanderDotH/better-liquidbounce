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
package net.ccbluex.liquidbounce.features.module.modules.movement.liquidwalk.modes

import net.ccbluex.liquidbounce.utils.block.collideBlockIntersects
import net.ccbluex.liquidbounce.utils.block.isBlockAtPosition
import net.ccbluex.liquidbounce.utils.entity.box
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.level.block.LiquidBlock

internal fun standingOnWater(player: LocalPlayer): Boolean {
    val boundingBox = player.box
    val detectionBox = boundingBox.setMinY(boundingBox.minY - 0.01)
    return detectionBox.isBlockAtPosition { it is LiquidBlock }
}

internal fun collidesWithAnythingElse(player: LocalPlayer): Boolean {
    val boundingBox = player.box
    val detectionBox = boundingBox.setMinY(boundingBox.minY - 0.5)
    return detectionBox.collideBlockIntersects { it !is LiquidBlock }
}
