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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.runtime.planning

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.SPEAR_KILL_PRIMED_ENDPOINT_EPSILON
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.withVanillaSpearKillBlockShapes
import net.ccbluex.liquidbounce.utils.client.player
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal fun SpearKillModuleState.isSpearKillPrimedEndpointFree(sessionOrigin: Vec3, position: Vec3): Boolean {
    if (!position.x.isFinite() || !position.y.isFinite() || !position.z.isFinite()) return false
    val destinationBox = spearKillServerCollisionBoxAt(sessionOrigin)
        .move(position.subtract(sessionOrigin))
        .deflate(SPEAR_KILL_PRIMED_ENDPOINT_EPSILON)
    val minimum = BlockPos.containing(destinationBox.minX, destinationBox.minY, destinationBox.minZ)
    val maximum = BlockPos.containing(destinationBox.maxX, destinationBox.maxY, destinationBox.maxZ)
    return world.hasChunksAt(minimum, maximum) &&
        world.worldBorder.isWithinBounds(destinationBox) &&
        withVanillaSpearKillBlockShapes { world.noCollision(player, destinationBox) }
}
