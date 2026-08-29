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

/**
 * Implemented by the isolated version-specific bridge and loaded only by class name.
 * Implementations must have a public no-argument constructor. [create] must probe every
 * upstream class and method used by the bridge, returning [LitematicaBridgeResult.Unsupported]
 * instead of allowing an incomplete port to escape.
 */
interface LitematicaPortFactory {
    fun create(): LitematicaBridgeResult
}

sealed interface LitematicaBridgeResult {
    data class Ready(val port: LitematicaPort) : LitematicaBridgeResult

    data class Unsupported(
        val capabilities: LitematicaCapabilities,
        val detail: String,
    ) : LitematicaBridgeResult
}
