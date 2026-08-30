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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.utils.client.world
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

internal fun SpearKillModuleState.spearKillCollisionBoxesAt(position: BlockPos.MutableBlockPos): List<AABB> =
    if (!world.hasChunkAt(position)) {
        listOf(AABB(
            position.x.toDouble(),
            position.y.toDouble(),
            position.z.toDouble(),
            position.x + 1.0,
            position.y + 1.0,
            position.z + 1.0,
        ))
    } else {
        world.getBlockState(position)
            .getCollisionShape(world, position)
            .toAabbs()
            .map { box -> box.move(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()) }
    }
