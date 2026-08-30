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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision


import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Tests [movement] as a raycast swept by the complete player hitbox.
 *
 * [AABB.collidedAlongVector] expands each collision box by the player's half extents and casts the
 * hitbox centre through that expanded shape. It is therefore an exact Minkowski-style hitbox cast,
 * not a centre-point ray or a sampled movement simulation.
 */
internal fun hasSpearKillHitboxRaycastCollision(
    playerBoundingBox: AABB,
    movement: Vec3,
    collisionBoxes: List<AABB>,
): Boolean {
    if (!playerBoundingBox.hasFiniteSpearKillHitboxRaycastCoordinates() ||
        !movement.hasFiniteSpearKillHitboxRaycastCoordinates() ||
        collisionBoxes.any { !it.hasFiniteSpearKillHitboxRaycastCoordinates() }
    ) {
        return true
    }

    return playerBoundingBox.collidedAlongVector(movement, collisionBoxes)
}

private fun AABB.hasFiniteSpearKillHitboxRaycastCoordinates(): Boolean =
    minX.isFinite() && minY.isFinite() && minZ.isFinite() &&
        maxX.isFinite() && maxY.isFinite() && maxZ.isFinite()

private fun Vec3.hasFiniteSpearKillHitboxRaycastCoordinates(): Boolean =
    x.isFinite() && y.isFinite() && z.isFinite()
