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
package net.ccbluex.liquidbounce.features.module.modules.world.trialchamber

import net.minecraft.world.phys.Vec3

internal data class TrialChamberRenderSnapshotKey(
    val worldEpoch: Long,
    val revision: Long,
) {
    init {
        require(worldEpoch >= 0) { "Render snapshot world epoch must not be negative" }
        require(revision >= 0) { "Render snapshot revision must not be negative" }
    }
}

/** Reuses filtered and sorted render plans until the snapshot, settings, or camera region changes. */
internal class TrialChamberRenderPlanCache(
    private val cameraReplanDistance: Double = DEFAULT_CAMERA_REPLAN_DISTANCE,
) {

    private var cachedKey: TrialChamberRenderSnapshotKey? = null
    private var cachedCameraPosition: Vec3? = null
    private var cachedSettings: TrialChamberRenderSettings? = null
    private var cachedPlan = TrialChamberRenderPlan.EMPTY

    init {
        require(cameraReplanDistance.isFinite() && cameraReplanDistance > 0.0) {
            "Camera replan distance must be finite and positive"
        }
    }

    fun resolve(
        key: TrialChamberRenderSnapshotKey,
        cameraPosition: Vec3,
        targets: Collection<TrialChamberRenderTarget>,
        settings: TrialChamberRenderSettings,
    ): TrialChamberRenderPlan {
        if (!requiresReplan(key, cameraPosition, settings)) return cachedPlan

        cachedKey = key
        cachedCameraPosition = cameraPosition
        cachedSettings = settings
        cachedPlan = TrialChamberRenderPlanner.plan(TrialChamberRenderRequest(cameraPosition, targets, settings))
        return cachedPlan
    }

    fun reset() {
        cachedKey = null
        cachedCameraPosition = null
        cachedSettings = null
        cachedPlan = TrialChamberRenderPlan.EMPTY
    }

    private fun requiresReplan(
        key: TrialChamberRenderSnapshotKey,
        cameraPosition: Vec3,
        settings: TrialChamberRenderSettings,
    ): Boolean {
        if (key != cachedKey || settings != cachedSettings) return true
        val cachedCamera = cachedCameraPosition ?: return true
        return cachedCamera.distanceToSqr(cameraPosition) >= cameraReplanDistance * cameraReplanDistance
    }

    companion object {
        const val DEFAULT_CAMERA_REPLAN_DISTANCE = 1.0
    }
}
