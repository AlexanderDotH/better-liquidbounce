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
import net.minecraft.util.AbortableIterationConsumer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.level.entity.LevelEntityGetter
import java.util.function.Consumer
import java.util.function.Predicate

fun <B : Entity, T : B> LevelEntityGetter<B>.forEach(
    type: EntityTypeTest<B, T>,
    consumer: Consumer<T>,
) = this.get(type, AbortableIterationConsumer.forConsumer(consumer))

fun <B : Entity, T : B, C : MutableCollection<in T>> LevelEntityGetter<B>.filterTo(
    destination: C,
    type: EntityTypeTest<B, T>,
    predicate: Predicate<T> = Predicates.alwaysTrue(),
): C {
    this.forEach(type) { if (predicate.test(it)) destination += it }
    return destination
}

fun <B : Entity, T : B> LevelEntityGetter<B>.filter(
    type: EntityTypeTest<B, T>,
    predicate: Predicate<T>,
): List<T> = this.filterTo(ArrayList(), type, predicate)

fun <B : Entity, T : B> LevelEntityGetter<B>.firstOrNull(
    type: EntityTypeTest<B, T>,
    predicate: Predicate<T>,
): T? {
    var ref: T? = null
    this.get(type) {
        if (predicate.test(it)) {
            ref = it
            AbortableIterationConsumer.Continuation.ABORT
        } else {
            AbortableIterationConsumer.Continuation.CONTINUE
        }
    }
    return ref
}

fun <B : Entity, T : B> LevelEntityGetter<B>.none(
    type: EntityTypeTest<B, T>,
    predicate: Predicate<T>,
): Boolean = firstOrNull(type, predicate) == null

fun <B : Entity, T : B> LevelEntityGetter<B>.any(
    type: EntityTypeTest<B, T>,
    predicate: Predicate<T>,
): Boolean = firstOrNull(type, predicate) != null
