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
package net.ccbluex.liquidbounce.features.module.modules.world.holefiller.model

import net.ccbluex.liquidbounce.utils.block.hole.Hole
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.structure.BoundingBox

@JvmRecord
internal data class HoleFillerPlanContext(
    val holes: List<Hole>,
    val selfInHole: Boolean,
    val selfRegion: BoundingBox,
    val blocks: MutableSet<BlockPos>,
)
