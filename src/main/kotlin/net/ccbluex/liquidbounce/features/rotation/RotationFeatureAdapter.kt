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

package net.ccbluex.liquidbounce.features.rotation

import net.ccbluex.liquidbounce.features.combat.contract.CombatRuntimeEnvironment
import net.ccbluex.liquidbounce.features.module.modules.movement.freeze.contract.FreezeStateHook
import net.ccbluex.liquidbounce.features.rotation.contract.RotationLagState
import net.ccbluex.liquidbounce.utils.aiming.RotationEnvironmentBridge
import net.ccbluex.liquidbounce.utils.aiming.RotationEnvironmentProvider

object RotationFeatureAdapter : RotationEnvironmentProvider {
    fun install() = RotationEnvironmentBridge.install(this)

    override fun isFakeLagging(): Boolean = RotationLagState.isFakeLagging()

    override fun isFreezing(): Boolean = FreezeStateHook.isRunning()

    override fun shouldPauseRotation(): Boolean = CombatRuntimeEnvironment.shouldPauseRotation()
}
