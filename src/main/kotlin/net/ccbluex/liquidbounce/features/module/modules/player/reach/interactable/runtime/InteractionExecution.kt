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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.runtime

import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

internal fun MinecraftReachInteractableRuntime.normalInteractionAvailable(): Boolean = when (val hit = mc.hitResult) {
    is BlockHitResult -> hit.type == HitResult.Type.BLOCK
    is EntityHitResult -> mc.player?.isWithinEntityInteractionRange(hit.entity, 0.0) == true
    else -> false
}

internal fun Vec3.eyePosition(): Vec3 {
    val player = requireNotNull(mc.player)
    return add(0.0, player.getEyeHeight(Pose.STANDING).toDouble(), 0.0)
}
