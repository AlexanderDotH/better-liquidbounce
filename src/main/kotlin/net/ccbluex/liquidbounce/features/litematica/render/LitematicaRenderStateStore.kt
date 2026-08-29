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
package net.ccbluex.liquidbounce.features.litematica.render

import net.minecraft.core.BlockPos
import java.util.concurrent.atomic.AtomicReference

interface LitematicaRenderSink {
    fun update(snapshot: LitematicaRenderSnapshot)
    fun clear()
}

class LitematicaRenderStateStore : LitematicaRenderSink {
    private val current = AtomicReference(LitematicaRenderSnapshot.EMPTY)

    override fun update(snapshot: LitematicaRenderSnapshot) {
        current.set(snapshot.immutableCopy())
    }

    override fun clear() {
        current.set(LitematicaRenderSnapshot.EMPTY)
    }

    fun snapshot(): LitematicaRenderSnapshot = current.get()
}

private fun LitematicaRenderSnapshot.immutableCopy() = copy(
    targets = java.util.List.copyOf(targets.map(LitematicaRenderTarget::immutableCopy)),
    hud = hud?.immutableCopy(),
)

private fun LitematicaRenderTarget.immutableCopy() = copy(position = position.immutableCopy())

private fun LitematicaHudSnapshot.immutableCopy() = copy(currentTarget = currentTarget?.immutableCopy())

private fun BlockPos.immutableCopy() = BlockPos(x, y, z)
