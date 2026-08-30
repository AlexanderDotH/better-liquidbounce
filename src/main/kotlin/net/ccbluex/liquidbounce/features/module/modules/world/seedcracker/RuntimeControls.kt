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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker

import net.ccbluex.liquidbounce.event.events.NotificationEvent

internal fun RuntimeState.pause(): SeedCrackerPresentation = if (tracker.pause()) {
    netherSearchProgress.updateAndGet { it?.copy(paused = true) }
    activeScope.get()?.let(::persist)
    refreshStatusProjection()
    presentation("paused", NotificationEvent.Severity.INFO)
} else {
    presentation("alreadyPaused", NotificationEvent.Severity.INFO)
}

internal fun RuntimeState.resume(): SeedCrackerPresentation = if (tracker.resume()) {
    netherSearchProgress.updateAndGet { it?.copy(paused = false) }
    refreshStatusProjection()
    presentation("resumed", NotificationEvent.Severity.INFO)
} else {
    presentation("alreadyRunning", NotificationEvent.Severity.INFO)
}

internal fun RuntimeState.resetCurrent(): SeedCrackerPresentation {
    val scope = activeScope.get() ?: return presentation("noWorld", NotificationEvent.Severity.ERROR)
    ledger.clearBlocking(scope)
    clearVolatileEvidence()
    candidate.set(null)
    latestSolveResult.set(null)
    tracker.reset()
    refreshStatusProjection()
    publishGuidanceIfChanged(force = true)
    return presentation("resetCurrent", NotificationEvent.Severity.SUCCESS)
}

internal fun RuntimeState.resetAll(): SeedCrackerPresentation {
    ledger.clearAllBlocking()
    clearVolatileEvidence()
    candidate.set(null)
    latestSolveResult.set(null)
    tracker.reset()
    refreshStatusProjection()
    publishGuidanceIfChanged(force = true)
    return presentation("resetAll", NotificationEvent.Severity.SUCCESS)
}
