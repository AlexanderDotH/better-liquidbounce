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
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.maceclip.research




import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
/** Small command-facing port installed by MaceKill's route owner. */
internal interface MaceClipResearchControl {
    fun startProbe(request: MaceClipResearchProbeRequest): MaceClipResearchProbeStartResult
    fun status(): MaceClipResearchStatus
    fun abort(): MaceClipResearchAbortResult
}

/** Applies the fail-closed ownership checks before handing a request to the packet executor. */
@Suppress("LongParameterList")
internal class MaceClipResearchGuardedControl(
    private val hasActiveProbe: () -> Boolean,
    private val hasActiveRemoteKillSession: () -> Boolean,
    private val hasUnsafeMovementContext: () -> Boolean,
    private val startExecution: (MaceClipResearchProbeRequest) -> MaceClipResearchProbeStartResult,
    private val statusProvider: () -> MaceClipResearchStatus,
    private val abortExecution: () -> MaceClipResearchAbortResult,
) : MaceClipResearchControl {

    override fun startProbe(request: MaceClipResearchProbeRequest): MaceClipResearchProbeStartResult {
        if (!request.isValid()) return MaceClipResearchProbeStartResult.ROUTE_REJECTED
        if (hasActiveProbe()) return MaceClipResearchProbeStartResult.ACTIVE_PROBE
        if (hasActiveRemoteKillSession()) return MaceClipResearchProbeStartResult.ACTIVE_REMOTE_KILL_SESSION
        if (hasUnsafeMovementContext()) return MaceClipResearchProbeStartResult.UNSAFE_CONTEXT
        return startExecution(request)
    }

    override fun status() = statusProvider()
    override fun abort() = abortExecution()
}

internal object MaceClipResearchControlRegistry : MaceClipResearchControl {

    @Volatile
    private var delegate: MaceClipResearchControl = UnavailableMaceClipResearchControl

    fun install(control: MaceClipResearchControl) {
        delegate = control
    }

    fun uninstall(control: MaceClipResearchControl) {
        if (delegate === control) delegate = UnavailableMaceClipResearchControl
    }

    override fun startProbe(request: MaceClipResearchProbeRequest) = delegate.startProbe(request)
    override fun status() = delegate.status()
    override fun abort() = delegate.abort()
}

private object UnavailableMaceClipResearchControl : MaceClipResearchControl {
    override fun startProbe(request: MaceClipResearchProbeRequest) =
        MaceClipResearchProbeStartResult.INVALID_CONTEXT

    override fun status(): MaceClipResearchStatus = MaceClipResearchStatus.Idle
    override fun abort() = MaceClipResearchAbortResult.IDLE
}
