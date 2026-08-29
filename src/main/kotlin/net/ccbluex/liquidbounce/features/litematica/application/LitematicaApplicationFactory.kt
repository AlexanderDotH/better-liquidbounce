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
package net.ccbluex.liquidbounce.features.litematica.application

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaAvailability
import net.ccbluex.liquidbounce.features.litematica.integration.loader.LitematicaPortLoadResult
import net.ccbluex.liquidbounce.features.litematica.integration.loader.LitematicaPortLoader
import net.ccbluex.liquidbounce.features.litematica.render.LitematicaRenderBridge
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.utils.aiming.RotationsValueGroup

sealed interface LitematicaApplicationCreation {
    data class Ready(val application: LitematicaApplication) : LitematicaApplicationCreation
    data class Unavailable(val availability: LitematicaAvailability.Unavailable) : LitematicaApplicationCreation
}

class LitematicaApplicationFactory(
    private val loadPort: () -> LitematicaPortLoadResult = LitematicaPortLoader::load,
) {

    fun create(
        module: ClientModule,
        rotations: RotationsValueGroup,
        setPrinterToggle: (Boolean) -> Unit,
    ): LitematicaApplicationCreation = when (val loaded = loadPort()) {
        is LitematicaPortLoadResult.Unavailable -> LitematicaApplicationCreation.Unavailable(loaded.availability)
        is LitematicaPortLoadResult.Ready -> LitematicaApplicationCreation.Ready(
            LitematicaApplication(
                port = loaded.port,
                actionDriver = MinecraftLitematicaActionDriver(module, rotations),
                conflictSource = MinecraftLitematicaConflictSource(module),
                renderSink = LitematicaRenderBridge,
                setPrinterToggle = setPrinterToggle,
            ),
        )
    }
}
