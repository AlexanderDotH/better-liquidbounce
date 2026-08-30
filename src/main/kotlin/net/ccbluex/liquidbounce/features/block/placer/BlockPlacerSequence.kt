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
package net.ccbluex.liquidbounce.features.block.placer

import net.minecraft.core.BlockPos

internal fun shortestSupportPath(candidates: Iterable<Set<BlockPos>?>): Set<BlockPos>? {
    var best: Set<BlockPos>? = null
    for (candidate in candidates) {
        if (candidate == null) continue
        if (best == null || best.size > candidate.size) {
            best = candidate
        }
        if (candidate.size <= 1) break
    }
    return best
}

internal inline fun runInstantPlacement(sendRotation: Boolean, send: () -> Unit, place: () -> Unit) {
    if (sendRotation) send()
    place()
}

internal inline fun clearCrystalTarget(clear: () -> Unit) = clear()

internal inline fun runCrystalAttack(
    attack: () -> Unit,
    resetDelay: () -> Unit,
    clearTarget: () -> Unit,
) {
    attack()
    resetDelay()
    clearTarget()
}
