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

@file:JvmName("BlockExtensionsKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.utils.block

import com.google.common.base.Predicates
import net.ccbluex.fastutil.weightedFilterSortedByAtMost
import it.unimi.dsi.fastutil.booleans.BooleanObjectPair
import net.ccbluex.liquidbounce.utils.client.interaction
import net.ccbluex.liquidbounce.utils.client.isOlderThan1_21_2
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.client.network
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.math.boundsOrNull
import net.ccbluex.liquidbounce.utils.math.distanceToSqr
import net.ccbluex.liquidbounce.utils.math.iterator
import net.ccbluex.liquidbounce.utils.math.plus
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.network.useItem
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.phys.AABB
import java.util.function.Predicate
import kotlin.math.ceil
import kotlin.math.floor

fun BlockPos.hasAnySolidPlacementNeighbor(): Boolean {
    val cache = BlockPos.MutableBlockPos()
    return Direction.entries.any {
        !cache.setWithOffset(this, it).stateOrEmpty.canBeReplaced()
    }
}

fun BlockPos.isBlockedByEntities(
    except: Entity? = null,
    box: AABB = FULL_BLOCK_BOX,
    predicate: Predicate<Entity> = Predicates.alwaysTrue(),
): Boolean {
    val posBox = box + this
    return world.getEntities(except, posBox, EntitySelector.NO_SPECTATORS.and(predicate))
        .isNotEmpty() // TODO: optimize this
}

fun BlockPos.getBlockingEntities(
    except: Entity? = null,
    box: AABB = FULL_BLOCK_BOX,
    predicate: Predicate<Entity> = Predicates.alwaysTrue(),
): List<Entity> {
    val posBox = box + this
    return world.getEntities(except, posBox, EntitySelector.NO_SPECTATORS.and(predicate))
}

/**
 * Like [isBlockedByEntities] but it returns a blocking end crystal if present.
 */
fun BlockPos.isBlockedByEntitiesReturnCrystal(
    except: Entity? = null,
    box: AABB = FULL_BLOCK_BOX,
    excludeIds: IntArray? = null
): BooleanObjectPair<EndCrystal?> {
    var blocked = false

    val posBox = box + this
    val selector = Predicate<Entity> {
        EntitySelector.NO_SPECTATORS.test(it) && (excludeIds == null || it.id !in excludeIds)
    }
    world.getEntities(except, posBox, selector).forEach {
        if (it is EndCrystal) {
            return BooleanObjectPair.of(true, it)
        }

        blocked = true
    }

    return BooleanObjectPair.of(blocked, null)
}
