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
package net.ccbluex.liquidbounce.features.litematica.integration.api

import net.minecraft.core.BlockPos

fun interface LitematicaPlacementPositionProvider {
    fun position(): BlockPos?
}

interface LitematicaPositionProviderLease : AutoCloseable {
    val isActive: Boolean

    /** Must be safe to call repeatedly. */
    override fun close()

    companion object {
        @JvmField
        val NONE: LitematicaPositionProviderLease = object : LitematicaPositionProviderLease {
            override val isActive: Boolean = false
            override fun close() = Unit
        }
    }
}
