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
package net.ccbluex.liquidbounce.features.trialchamber

internal enum class TrialChamberContinuity {
    STARTED,
    CONTINUED,
    CHANGED,
}

/**
 * Retains the identity of the selected chamber while its loaded anchor subset changes.
 *
 * Trial Chamber chunks do not arrive atomically. Remembering every anchor already associated with the session lets
 * a newly loaded, topologically connected subset rejoin it even if the one shared anchor was briefly unloaded.
 */
internal class TrialChamberSessionContinuity {

    private val knownAnchorPositions = linkedSetOf<TrialBlockPosition>()

    fun observe(cluster: TrialChamberCluster): TrialChamberContinuity {
        val observedPositions = cluster.anchors.mapTo(linkedSetOf(), TrialChamberAnchor::position)
        if (knownAnchorPositions.isEmpty()) {
            knownAnchorPositions += observedPositions
            return TrialChamberContinuity.STARTED
        }

        val continuesSession = observedPositions.any { observed ->
            observed in knownAnchorPositions || knownAnchorPositions.any { known ->
                TrialChamberClusterer.areConnected(observed, known)
            }
        }
        if (continuesSession) {
            knownAnchorPositions += observedPositions
            return TrialChamberContinuity.CONTINUED
        }

        knownAnchorPositions.clear()
        knownAnchorPositions += observedPositions
        return TrialChamberContinuity.CHANGED
    }

    fun clear() {
        knownAnchorPositions.clear()
    }
}
