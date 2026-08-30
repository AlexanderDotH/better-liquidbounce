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
package net.ccbluex.liquidbounce.features.module.modules.world.holefiller.policy

import it.unimi.dsi.fastutil.booleans.BooleanDoubleImmutablePair
import net.ccbluex.liquidbounce.utils.block.hole.Hole
import net.minecraft.world.entity.Entity
import org.joml.Vector2d
import kotlin.math.acos

internal object HoleMovementPolicy {
    fun evaluate(hole: Hole, entity: Entity, checkMovement: Boolean): BooleanDoubleImmutablePair {
        val holePos = hole.positions.center
        val velocity = entity.position().subtract(entity.xo, entity.yo, entity.zo)
        val playerPos = entity.position()

        val normalizedVelocity = Vector2d(velocity.x, velocity.z).normalize()
        val normalizedDelta = Vector2d(holePos.x - playerPos.x, holePos.z - playerPos.z).normalize()
        val angle = acos(normalizedDelta.dot(normalizedVelocity))

        if (!checkMovement) {
            return BooleanDoubleImmutablePair(true, angle)
        }

        // cos(30°) = 0.866
        return BooleanDoubleImmutablePair(angle >= 0.866, angle)
    }
}
