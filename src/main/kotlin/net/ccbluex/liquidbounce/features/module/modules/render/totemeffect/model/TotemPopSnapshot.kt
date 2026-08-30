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
package net.ccbluex.liquidbounce.features.module.modules.render.totemeffect.model

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

data class TotemPopSnapshot(
    val pos: Vec3,
    val xRot: Float,
    val yRot: Float,
    val bbHeight: Float,
) {
    constructor(entity: Entity) : this(
        pos = entity.position(),
        xRot = entity.xRot,
        yRot = entity.yRot,
        bbHeight = entity.bbHeight,
    )
}
