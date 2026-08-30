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
@file:JvmName("TargetTrackerKt")
@file:JvmMultifileClass

package net.ccbluex.liquidbounce.features.combat.runtime

import net.ccbluex.fastutil.objectLinkedSetOf
import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.getActualHealth
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player

enum class TargetPriority(override val tag: String) : Tagged, Comparator<LivingEntity> {
    /**
     * Player first
     */
    TYPE("Type") {
        private fun weight(entity: LivingEntity): Int =
            when (entity) {
                is Player -> 0
                is Enemy -> 1
                is NeutralMob if entity.persistentAngerTarget == player.uuid -> 2
                else -> Int.MAX_VALUE
            }

        override fun compare(o1: LivingEntity, o2: LivingEntity): Int =
            weight(o1) compareTo weight(o2)
    },

    /**
     * Lowest health first
     */
    HEALTH("Health") {
        override fun compare(o1: LivingEntity, o2: LivingEntity): Int =
            o1.getActualHealth() compareTo o2.getActualHealth()
    },

    /**
     * Closest to you first
     */
    DISTANCE("Distance") {
        override fun compare(o1: LivingEntity, o2: LivingEntity): Int =
            o1.squaredBoxedDistanceTo(player) compareTo o2.squaredBoxedDistanceTo(player)
    },

    /**
     * Closest to your crosshair first
     */
    DIRECTION("Direction") {
        override fun compare(o1: LivingEntity, o2: LivingEntity): Int =
            RotationUtil.crosshairAngleToEntity(o1) compareTo RotationUtil.crosshairAngleToEntity(o2)
    },

    /**
     * With the lowest hurt time first
     */
    HURT_TIME("HurtTime") {
        override fun compare(o1: LivingEntity, o2: LivingEntity): Int =
            o1.hurtTime compareTo o2.hurtTime
    },

    /**
     * Oldest entity first
     */
    AGE("Age") {
        override fun compare(o1: LivingEntity, o2: LivingEntity): Int =
            o2.tickCount compareTo o1.tickCount
    },
}
