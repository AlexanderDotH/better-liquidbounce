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
package net.ccbluex.liquidbounce.render.target

import net.ccbluex.liquidbounce.annotations.ValueClassCandidate
import net.minecraft.world.entity.LivingEntity

internal fun targetHeartSlots(
    target: LivingEntity,
    dynamicCount: Boolean,
    configuredHeartCount: Int,
): List<TargetHeartSlot> = buildList {
    addTargetHeartSlots(target.health, target.absorptionAmount, dynamicCount, configuredHeartCount)
}

internal fun MutableList<TargetHeartSlot>.addTargetHeartSlots(
    health: Float,
    absorption: Float,
    dynamicCount: Boolean,
    configuredHeartCount: Int,
) {
    if (dynamicCount) {
        addHeartSlots(TargetHeartType.HEALTH, health)
    } else {
        repeat(configuredHeartCount) { add(TargetHeartSlot(TargetHeartType.HEALTH, 1f)) }
    }
    addHeartSlots(TargetHeartType.ABSORPTION, absorption)
}

private fun MutableList<TargetHeartSlot>.addHeartSlots(type: TargetHeartType, amount: Float) {
    val hearts = amount.coerceAtLeast(0f) / 2f
    repeat(hearts.toInt()) { add(TargetHeartSlot(type, 1f)) }
    val partialHeart = hearts - hearts.toInt()
    if (partialHeart > 0f) add(TargetHeartSlot(type, partialHeart))
}

@ValueClassCandidate
internal data class TargetHeartSlot(val type: TargetHeartType, val fill: Float)

internal enum class TargetHeartType { HEALTH, ABSORPTION }
