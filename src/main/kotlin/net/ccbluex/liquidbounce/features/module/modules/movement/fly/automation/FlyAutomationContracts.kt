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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.automation

internal enum class FlyAutomationOwnership {
    BARITONE,
    USER,
}

internal data class FlyAutomationLease internal constructor(
    val generation: Long,
    val modeName: String,
    val ownership: FlyAutomationOwnership,
)

internal sealed interface FlyAutomationAcquireResult {
    data class Acquired(val lease: FlyAutomationLease) : FlyAutomationAcquireResult
    data class Rejected(val reason: String) : FlyAutomationAcquireResult
}

internal sealed interface FlyAutomationLeaseValidation {
    data object Valid : FlyAutomationLeaseValidation
    data class Invalid(val reason: String) : FlyAutomationLeaseValidation
}

internal interface FlyAutomationRuntime {
    val enabled: Boolean
    val selectedModeName: String
    val selectedProfile: FlyAutomationProfile?

    fun setModuleEnabled(enabled: Boolean)
    fun enableSelectedMode()
    fun disableSelectedMode()
}

/** Module-facing port bound by the Fly composition root. */
internal interface FlyAutomationModulePort : FlyAutomationRuntime {
    val running: Boolean
}
