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
package net.ccbluex.liquidbounce.features.module.modules.movement.vclip

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleVClip
import net.minecraft.world.entity.Entity

internal abstract class VClipMovementMode(name: String) : Mode(name) {
    final override val parent: ModeValueGroup<*>
        get() = ModuleVClip.modes

    abstract fun clip(entity: Entity, origin: VClipPosition, target: VClipPosition)

    protected fun applyLocalPosition(
        entity: Entity,
        target: VClipPosition,
        resetMotion: Boolean,
        fallProtection: VClipFallProtection,
    ) {
        entity.absSnapTo(target.x, target.y, target.z)
        if (resetMotion) {
            entity.deltaMovement = entity.deltaMovement.multiply(0.0, 0.0, 0.0)
        }
        if (fallProtection.resetLocalFallDistance) {
            entity.resetFallDistance()
            if (entity !== player) {
                player.resetFallDistance()
            }
        }
    }
}
