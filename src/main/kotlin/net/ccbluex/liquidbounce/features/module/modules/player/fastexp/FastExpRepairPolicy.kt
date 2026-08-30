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
package net.ccbluex.liquidbounce.features.module.modules.player.fastexp

private const val REPAIR_RATE = 2
private const val EXPERIENCE_PER_BOTTLE = 7

/**
 * Converts repair damage into bottles using Minecraft's two durability per experience point
 * and the expected seven experience points per bottle. Integer truncation intentionally matches
 * the module's existing estimate before the available stack caps the result.
 */
internal fun requiredExperienceBottleCount(totalDamage: Int, availableBottles: Int): Int {
    val experienceRequired = totalDamage / REPAIR_RATE
    val bottlesRequired = experienceRequired / EXPERIENCE_PER_BOTTLE

    return bottlesRequired.coerceAtMost(availableBottles)
}
