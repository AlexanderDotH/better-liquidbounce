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

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.ccbluex.fastutil.objectLinkedSetOf
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.ValueType.FLOAT
import net.ccbluex.liquidbounce.config.types.ValueType.FLOAT_RANGE
import net.ccbluex.liquidbounce.config.types.ValueType.INT
import net.ccbluex.liquidbounce.config.types.ValueType.INT_RANGE
import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.utils.aiming.utils.RotationUtil
import net.ccbluex.liquidbounce.config.types.DummyRangedValueProvider
import net.ccbluex.liquidbounce.config.types.NoneRangedValueProvider
import net.ccbluex.liquidbounce.config.types.RangedValueProvider
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.ccbluex.liquidbounce.utils.entity.getActualHealth
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.math.sq
import net.ccbluex.liquidbounce.utils.sorting.ComparatorChain
import net.minecraft.world.entity.LivingEntity
import java.util.SequencedSet
import java.util.function.Predicate

/**
 * A target tracker to choose the best enemy to attack
 */
open class TargetTracker(
    defaultPriority: TargetPriority = TargetPriority.HEALTH,
    rangeValue: RangedValueProvider = NoneRangedValueProvider,
    fovRange: ClosedFloatingPointRange<Float> = DEFAULT_FOV_RANGE,
    defaultPriorities: SequencedSet<TargetPriority> = targetTrackerDefaultPriorities(defaultPriority),
) : TargetSelector(defaultPriority, rangeValue, fovRange, defaultPriorities) {

    constructor(
        defaultPriority: TargetPriority = TargetPriority.HEALTH,
        range: RangedValue<*>,
        fovRange: ClosedFloatingPointRange<Float> = DEFAULT_FOV_RANGE,
        defaultPriorities: SequencedSet<TargetPriority> = targetTrackerDefaultPriorities(defaultPriority),
    ) : this(defaultPriority, DummyRangedValueProvider(range), fovRange, defaultPriorities)

    var target: LivingEntity? = null

    fun selectFirst(predicate: Predicate<LivingEntity>? = null): LivingEntity? {
        val enemies = targets()
        val selected = if (predicate != null) enemies.firstOrNull(predicate::test) else enemies.firstOrNull()
        return selected.also { this.target = it }
    }

    fun <R> select(evaluator: (LivingEntity) -> R): R? {
        for (enemy in targets()) {
            val value = evaluator(enemy)
            if (value != null) {
                target = enemy
                return value
            }
        }

        reset()
        return null
    }

    fun reset() {
        target = null
    }

    fun validate(predicate: Predicate<LivingEntity>? = null) {
        val target = target ?: return

        if (!validate(target) || predicate != null && !predicate.test(target)) {
            reset()
        }
    }
}

open class TargetSelector(
    defaultPriority: TargetPriority = TargetPriority.HEALTH,
    rangeValue: RangedValueProvider = NoneRangedValueProvider,
    fovRange: ClosedFloatingPointRange<Float> = DEFAULT_FOV_RANGE,
    defaultPriorities: SequencedSet<TargetPriority> = targetTrackerDefaultPriorities(defaultPriority),
) : ValueGroup("Target") {

    constructor(
        defaultPriority: TargetPriority = TargetPriority.HEALTH,
        range: RangedValue<*>,
        fovRange: ClosedFloatingPointRange<Float> = DEFAULT_FOV_RANGE,
        defaultPriorities: SequencedSet<TargetPriority> = targetTrackerDefaultPriorities(defaultPriority),
    ) : this(defaultPriority, DummyRangedValueProvider(range), fovRange, defaultPriorities)

    var closestSquaredEnemyDistance: Double = 0.0
        private set

    private val range = rangeValue.register(this)
    private val fov by float("FOV", DEFAULT_FOV.coerceIn(fovRange), fovRange)
    private val hurtTime by int("HurtTime", 10, 0..10)

    private var comparator: Comparator<in LivingEntity> = ComparatorChain(
        comparisonFunctions = defaultPriorities.toTypedArray(),
    )

    @Suppress("unused", "UnusedPrivateProperty")
    private val priority by multiEnumChoice(
        name = "Priority",
        default = defaultPriorities,
        canBeNone = false,
    ).onChanged { set ->
        comparator = ComparatorChain(comparisonFunctions = set.toTypedArray())
    }

    /**
     * Counts available targets.
     */
    fun countTargets(): Int = world.entitiesForRendering().count { entity ->
        entity is LivingEntity && validate(entity)
    }

    /**
     * Update should be called to always pick the best target out of the current world context
     */
    fun targets(): MutableList<LivingEntity> {
        val entities = ObjectArrayList<LivingEntity>()

        for (entity in world.entitiesForRendering()) {
            if (entity is LivingEntity && validate(entity)) {
                entities.add(entity)
            }
        }

        if (entities.isEmpty) {
            return entities
        }

        entities.sortWith(this.comparator)

        // Update max distance squared
        closestSquaredEnemyDistance = entities.minOf { it.squaredBoxedDistanceTo(player) }

        return entities
    }

    open fun validate(entity: LivingEntity) =
        entity !== player
            && !entity.isRemoved
            && entity.hurtTime <= hurtTime
            && validateRange(entity)
            && entity.shouldBeAttacked()
            && fov >= RotationUtil.crosshairAngleToEntity(entity)

    private fun validateRange(entity: LivingEntity): Boolean {
        if (range == null) return true

        val distanceSq = entity.squaredBoxedDistanceTo(player)
        val range = range.get()

        @Suppress("UNCHECKED_CAST")
        return when (this.range.valueType) {
            FLOAT -> distanceSq <= (range as Float).sq()
            FLOAT_RANGE ->
                distanceSq >= (range as ClosedFloatingPointRange<Float>).start.sq()
                && distanceSq <= range.endInclusive.sq()
            INT -> distanceSq <= (range as Int).sq()
            INT_RANGE -> distanceSq >= (range as IntRange).first.sq() && distanceSq <= range.last.sq()
            else -> true
        }
    }

    val maxRange: Float
        get() {
            if (range == null) return Float.MAX_VALUE

            val value = range.get()

            @Suppress("UNCHECKED_CAST")
            return when (range.valueType) {
                FLOAT -> value as Float
                FLOAT_RANGE -> (value as ClosedFloatingPointRange<Float>).endInclusive
                INT -> (value as Int).toFloat()
                INT_RANGE -> (value as IntRange).last.toFloat()
                else -> Float.MAX_VALUE
            }
        }

}

internal const val DEFAULT_FOV = 180f
internal val DEFAULT_FOV_RANGE = 0f..DEFAULT_FOV

internal fun targetTrackerDefaultPriorities(
    defaultPriority: TargetPriority,
    explicitPriorities: SequencedSet<TargetPriority>? = null,
): SequencedSet<TargetPriority> = explicitPriorities ?: objectLinkedSetOf(TargetPriority.TYPE, defaultPriority)
