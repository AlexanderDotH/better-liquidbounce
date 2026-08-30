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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

internal enum class GravityType(override val tag: String) : Tagged {
    LINEAR("Linear"),
    PROJECTILE("Projectile");

    fun apply(target: LivingEntity, eyes: Vec3, pointTracker: PointTracker): Rotation? = when (this) {
        LINEAR -> Rotation.lookingAt(pointTracker.findPoint(eyes, target, 1).pos, eyes)
        PROJECTILE -> calculateFishingRodRotation(target)
    }
}
