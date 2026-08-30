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
package net.ccbluex.liquidbounce.bootstrap.command

import net.ccbluex.liquidbounce.deeplearn.command.ModelCommandIntegrationBridge
import net.ccbluex.liquidbounce.deeplearn.command.ModelCommandIntegrationProvider
import net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.modes.DebugCombatRecorder
import net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.modes.DebugCombatTrainerRecorder
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleClickGui

internal object ModelCommandIntegrationAdapter : ModelCommandIntegrationProvider {
    override val combatRecorderFolder
        get() = DebugCombatRecorder.folder
    override val trainerRecorderFolder
        get() = DebugCombatTrainerRecorder.folder

    override fun syncClickGui() = ModuleClickGui.sync()

    fun install() = ModelCommandIntegrationBridge.install(this)
}
