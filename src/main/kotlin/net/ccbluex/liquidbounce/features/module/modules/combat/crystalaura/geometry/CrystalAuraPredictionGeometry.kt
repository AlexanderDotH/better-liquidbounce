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
package net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.geometry

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

internal fun predictedEntityBoundingBox(original: AABB, simulatedPosition: Vec3): AABB {
    val halfWidth = abs(original.maxX - original.minX) / 2.0
    return AABB(
        simulatedPosition.x - halfWidth,
        simulatedPosition.y,
        simulatedPosition.z - halfWidth,
        simulatedPosition.x + halfWidth,
        simulatedPosition.y + original.maxY - original.minY,
        simulatedPosition.z + halfWidth,
    )
}
