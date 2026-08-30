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

package net.ccbluex.liquidbounce.common

import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.Vec3

object PlayerSimulationHooks {

    private var movementHook: (MoverType, Vec3) -> Vec3 = { _, movement -> movement }
    private var safeWalkHook: () -> Boolean = { false }

    fun install(
        movement: (MoverType, Vec3) -> Vec3,
        safeWalk: () -> Boolean,
    ) {
        movementHook = movement
        safeWalkHook = safeWalk
    }

    fun movement(type: MoverType, movement: Vec3): Vec3 = movementHook(type, movement)

    fun isSafeWalkEnabled(): Boolean = safeWalkHook()
}
