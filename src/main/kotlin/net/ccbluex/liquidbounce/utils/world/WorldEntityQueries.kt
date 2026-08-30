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

@file:JvmName("WorldExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.world

import com.google.common.base.Predicates
import net.ccbluex.liquidbounce.interfaces.LevelEntityAccess
import net.ccbluex.liquidbounce.utils.math.expandToCube
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.EntityGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.entity.LevelEntityGetter
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.function.Predicate

inline fun <reified T : Entity> EntityGetter.getEntitiesInCube(
    midPos: Vec3,
    range: Double,
    predicate: Predicate<T> = Predicates.alwaysTrue(),
): MutableList<T> {
    return getEntitiesOfClass(
        T::class.java,
        midPos.expandToCube(range),
        predicate,
    ) // -> ArrayList
}

fun EntityGetter.getEntitiesInCube(
    midPos: Vec3,
    range: Double,
    exclusion: Entity? = null,
    predicate: Predicate<Entity> = Predicates.alwaysTrue(),
): MutableList<Entity> {
    val size = range * 2.0
    val box = AABB.ofSize(midPos, size, size, size)
    return getEntities(exclusion, box, predicate) // -> ArrayList
}

val Level.entityGetter: LevelEntityGetter<Entity>
    inline get() = LevelEntityAccess.getEntities(this)
