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

/** Owns one generation-tagged steering lease and all reversible Fly lifecycle transitions. */
internal class FlyAutomationController(
    private val runtime: FlyAutomationRuntime,
) {

    private data class Session(
        val lease: FlyAutomationLease,
        var phase: Phase = Phase.ACTIVE,
        var intent: FlySteeringIntent? = null,
        var externalStateChange: String? = null,
        var externalModuleStateChange: Boolean = false,
        var pendingAutomaticEnd: FlyAutomationEnd? = null,
    )

    private enum class Phase {
        ACTIVE,
        USER_RUNTIME_SUSPENDED,
        MODULE_DISABLED,
    }

    private var generation = 0L
    private var session: Session? = null
    private var moduleTransitionInProgress = false

    val runtimeSuspended: Boolean
        get() = session?.phase == Phase.USER_RUNTIME_SUSPENDED

    fun acquire(): FlyAutomationAcquireResult {
        if (session != null) return FlyAutomationAcquireResult.Rejected("Fly automation is already leased")
        if (runtime.selectedProfile == null) {
            return FlyAutomationAcquireResult.Rejected("${runtime.selectedModeName} has no Fly automation profile")
        }

        val ownership = if (runtime.enabled) FlyAutomationOwnership.USER else FlyAutomationOwnership.BARITONE
        val lease = FlyAutomationLease(++generation, runtime.selectedModeName, ownership)
        session = Session(lease)
        if (ownership == FlyAutomationOwnership.BARITONE) {
            setModuleEnabled(true)
        }

        val validation = validate(lease)
        if (validation is FlyAutomationLeaseValidation.Invalid) {
            release(lease)
            return FlyAutomationAcquireResult.Rejected(validation.reason)
        }
        return FlyAutomationAcquireResult.Acquired(lease)
    }

    fun validate(lease: FlyAutomationLease): FlyAutomationLeaseValidation {
        val current = session ?: return invalid("Fly automation lease is no longer active")
        val expectedEnabled = current.phase != Phase.MODULE_DISABLED
        val externalStateChange = current.externalStateChange
        return when {
            current.lease.generation != lease.generation -> invalid("Fly automation lease is stale")
            externalStateChange != null -> invalid(externalStateChange)
            runtime.selectedModeName != lease.modeName -> invalid("Fly mode changed to ${runtime.selectedModeName}")
            runtime.selectedProfile == null -> invalid("${runtime.selectedModeName} has no Fly automation profile")
            runtime.enabled != expectedEnabled -> invalid("Fly enabled state changed outside the automation lease")
            else -> FlyAutomationLeaseValidation.Valid
        }
    }

    fun temporarilySuspend(lease: FlyAutomationLease): Boolean {
        val current = validSession(lease) ?: return false
        if (current.phase != Phase.ACTIVE) return true
        current.intent = null

        when (lease.ownership) {
            FlyAutomationOwnership.USER -> {
                current.phase = Phase.USER_RUNTIME_SUSPENDED
                runtime.disableSelectedMode()
            }
            FlyAutomationOwnership.BARITONE -> {
                current.phase = Phase.MODULE_DISABLED
                setModuleEnabled(false)
            }
        }
        return true
    }

    fun resume(lease: FlyAutomationLease): Boolean {
        val current = validSession(lease) ?: return false
        when (current.phase) {
            Phase.ACTIVE -> return true
            Phase.USER_RUNTIME_SUSPENDED -> {
                current.phase = Phase.ACTIVE
                runtime.enableSelectedMode()
            }
            Phase.MODULE_DISABLED -> {
                current.phase = Phase.ACTIVE
                setModuleEnabled(true)
            }
        }
        return validate(lease) is FlyAutomationLeaseValidation.Valid
    }

    fun release(lease: FlyAutomationLease) {
        val current = session ?: return
        if (current.lease.generation != lease.generation) return

        val modeStillSelected = runtime.selectedModeName == lease.modeName
        val userIntervened = current.externalStateChange != null || !modeStillSelected
        val restoreUserMode = lease.ownership == FlyAutomationOwnership.USER &&
            current.phase == Phase.USER_RUNTIME_SUSPENDED && runtime.enabled && !current.externalModuleStateChange
        val disableOwnedModule = lease.ownership == FlyAutomationOwnership.BARITONE &&
            current.phase == Phase.ACTIVE && runtime.enabled && !userIntervened

        session = null
        if (restoreUserMode) runtime.enableSelectedMode()
        if (disableOwnedModule) setModuleEnabled(false)
    }

    fun applySteering(lease: FlyAutomationLease, intent: FlySteeringIntent): Boolean {
        val current = validSession(lease) ?: return false
        if (current.phase != Phase.ACTIVE) return false
        current.intent = intent
        return true
    }

    fun clearSteering(lease: FlyAutomationLease) {
        val current = session ?: return
        if (current.lease.generation == lease.generation) current.intent = null
    }

    fun activeIntent(): FlySteeringIntent? {
        val current = session ?: return null
        if (current.phase != Phase.ACTIVE) return null
        if (validate(current.lease) !is FlyAutomationLeaseValidation.Valid) return null
        return current.intent
    }

    fun profile(lease: FlyAutomationLease): FlyAutomationProfile? {
        if (validate(lease) !is FlyAutomationLeaseValidation.Valid) return null
        return runtime.selectedProfile
    }

    fun consumeAutomaticEnd(lease: FlyAutomationLease): FlyAutomationEnd? {
        val current = matchingSession(lease) ?: return null
        current.pendingAutomaticEnd?.let { end ->
            current.pendingAutomaticEnd = null
            return end
        }

        val end = runtime.selectedProfile?.consumeAutomaticEnd() ?: return null
        acceptAutomaticEnd(current)
        return end
    }

    fun markAutomaticEnd(reason: String) {
        val current = session ?: return
        current.intent = null
        current.pendingAutomaticEnd = FlyAutomationEnd(reason)
    }

    fun onModuleStateChanged(enabled: Boolean) {
        if (moduleTransitionInProgress) return
        val current = session ?: return
        current.intent = null
        if (!enabled && current.pendingAutomaticEnd != null) {
            acceptAutomaticEnd(current, moduleDisabled = true)
            return
        }
        if (!enabled && runtime.selectedModeName == current.lease.modeName) {
            val automaticEnd = runtime.selectedProfile?.consumeAutomaticEnd()
            if (automaticEnd != null) {
                acceptAutomaticEnd(current, moduleDisabled = true)
                current.pendingAutomaticEnd = automaticEnd
                return
            }
        }
        current.externalModuleStateChange = true
        current.externalStateChange = if (enabled) "Fly was enabled manually" else "Fly was disabled manually"
    }

    fun onSelectedModeChanged(modeName: String) {
        val current = session ?: return
        current.intent = null
        current.externalStateChange = "Fly mode changed to $modeName"
    }

    private fun validSession(lease: FlyAutomationLease): Session? {
        if (validate(lease) !is FlyAutomationLeaseValidation.Valid) return null
        return session
    }

    private fun matchingSession(lease: FlyAutomationLease): Session? {
        val current = session ?: return null
        if (current.lease.generation != lease.generation) return null
        if (runtime.selectedModeName != lease.modeName) return null
        return current
    }

    private fun acceptAutomaticEnd(
        current: Session,
        moduleDisabled: Boolean = !runtime.enabled,
    ) {
        current.intent = null
        current.externalStateChange = null
        current.externalModuleStateChange = false
        if (moduleDisabled) current.phase = Phase.MODULE_DISABLED
    }

    private fun setModuleEnabled(enabled: Boolean) {
        if (runtime.enabled == enabled) return
        moduleTransitionInProgress = true
        try {
            runtime.setModuleEnabled(enabled)
        } finally {
            moduleTransitionInProgress = false
        }
    }

    private fun invalid(reason: String) = FlyAutomationLeaseValidation.Invalid(reason)
}
