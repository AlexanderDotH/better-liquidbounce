/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.autoshoot

import net.ccbluex.liquidbounce.common.Tagged
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.ccbluex.liquidbounce.features.aiming.point.PointTracker
import net.ccbluex.liquidbounce.utils.aiming.projectiles.SituationalProjectileAngleCalculator
import net.ccbluex.liquidbounce.utils.render.trajectory.TrajectoryInfo
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.SnowballItem
import net.minecraft.world.phys.Vec3

internal fun calculateFishingRodRotation(target: LivingEntity): Rotation? =
    SituationalProjectileAngleCalculator.calculateAngleForEntity(TrajectoryInfo.FISHING_ROD, target)

internal enum class AutoShootGravityType(override val tag: String) : Tagged {
    AUTO("Auto"),
    LINEAR("Linear"),
    PROJECTILE("Projectile");

    fun rotationFor(
        target: LivingEntity,
        item: Item,
        eyes: Vec3,
        pointTracker: PointTracker,
    ): Rotation? = when (effectiveType(item)) {
        AUTO -> error("AUTO always resolves to a concrete gravity type")
        LINEAR -> Rotation.lookingAt(pointTracker.findPoint(eyes, target, 1).pos, eyes)
        PROJECTILE -> SituationalProjectileAngleCalculator.calculateAngleForEntity(TrajectoryInfo.GENERIC, target)
    }

    private fun effectiveType(item: Item): AutoShootGravityType = when (this) {
        AUTO -> if (item is EggItem || item is SnowballItem) PROJECTILE else LINEAR
        else -> this
    }
}
