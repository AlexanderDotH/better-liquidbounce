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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.createSpearKillServerPacketSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.resolveSpearKillServerPacketMovement
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarSegmentValidator
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.withVanillaSpearKillBlockShapes
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.createFastSpearKillSegmentValidator
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.createServerValidatedSpearKillDirectPacketSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
): SpearKillAStarSegmentValidator {
    val fastValidator = createFastSpearKillSegmentValidator(origin, playerBoundingBox)
    val serverValidator = createServerMovementSpearKillSegmentValidator(origin, playerBoundingBox)
    return SpearKillAStarSegmentValidator { from, to ->
        fastValidator.isClear(from, to) && serverValidator.isClear(from, to)
    }
}

internal fun SpearKillModuleState.createServerMovementSpearKillSegmentValidator(
    origin: Vec3,
    playerBoundingBox: AABB,
) = createSpearKillServerPacketSegmentValidator(
    origin = origin,
    playerBoundingBox = playerBoundingBox,
    hasDestinationCollision = { box ->
        withVanillaSpearKillBlockShapes { !world.noCollision(player, box) }
    },
    resolveMovement = { box, movement ->
        withVanillaSpearKillBlockShapes {
            resolveSpearKillServerPacketMovement(player, box, movement)
        }
    },
)
