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
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

internal data class BaseFinderRenderScope(
    val serverKey: String,
    val dimensionKey: String,
    val worldEpoch: Long,
    val revision: Long = 0L,
)

internal data class BaseFinderRenderContribution(
    val label: String,
    val score: Int,
    val observationText: String? = null,
)

internal data class BaseFinderRenderEvidence(
    val family: String,
    val score: Int,
    val detections: List<String> = emptyList(),
    val contributions: List<BaseFinderRenderContribution> = emptyList(),
    val showFamilyScore: Boolean = true,
)

internal data class BaseFinderRenderMarker(
    val id: String,
    val serverKey: String,
    val dimensionKey: String,
    val worldEpoch: Long,
    val anchor: Vec3,
    val confidence: Int,
    val topEvidenceKeys: List<String>,
    val updatedAtMillis: Long,
    val evidenceDetails: List<BaseFinderRenderEvidence> = emptyList(),
    val bounds: BaseFinderBounds? = null,
    val revision: Long = 0L,
)

internal data class BaseFinderRenderPlanSettings(
    val minimumConfidence: Int,
    val maximumDistance: Double,
    val renderLimit: Int,
    val boxRadius: Double,
    val boxHeight: Double,
    val lowConfidenceColor: Color4b,
    val highConfidenceColor: Color4b,
    val showLabels: Boolean,
    val maxLabels: Int,
    val baseLabel: String = "Base",
    val unknownEvidenceLabel: String = "Unknown",
    val distanceSuffix: String = "m",
    val labelScale: Float = 1f,
    val showEvidenceDetails: Boolean = true,
    val maxEvidenceDetails: Int = 4,
    val boxMode: BaseFinderBoxMode = BaseFinderBoxMode.FIXED,
    val dynamicPadding: Int = 1,
)

internal data class BaseFinderRenderRequest(
    val scope: BaseFinderRenderScope,
    val cameraPosition: Vec3,
    val settings: BaseFinderRenderPlanSettings,
    val markers: List<BaseFinderRenderMarker>,
    val nowMillis: Long,
) {
    companion object {
        fun fromSnapshot(
            snapshot: BaseFinderRenderSnapshot,
            cameraPosition: Vec3,
            settings: BaseFinderRenderPlanSettings,
            nowMillis: Long,
        ): BaseFinderRenderRequest {
            val scope = BaseFinderRenderScope(
                snapshot.serverKey, snapshot.dimensionKey, snapshot.worldEpoch, snapshot.revision,
            )
            val markers = java.util.List.copyOf(snapshot.markers).map { marker -> marker.toRenderMarker(snapshot) }
            return BaseFinderRenderRequest(scope, cameraPosition, settings, java.util.List.copyOf(markers), nowMillis)
        }
    }
}

private fun BaseFinderMarker.toRenderMarker(snapshot: BaseFinderRenderSnapshot) = BaseFinderRenderMarker(
    id = id,
    serverKey = snapshot.serverKey,
    dimensionKey = snapshot.dimensionKey,
    worldEpoch = snapshot.worldEpoch,
    anchor = Vec3(anchor.x.toDouble(), anchor.y.toDouble(), anchor.z.toDouble()),
    confidence = confidence,
    topEvidenceKeys = java.util.List.copyOf(topEvidenceKeys),
    updatedAtMillis = updatedAtMillis,
    evidenceDetails = evidenceDetails.toRenderEvidence(),
    bounds = bounds,
    revision = snapshot.revision,
)

internal data class BaseFinderRenderEntry(
    val marker: BaseFinderRenderMarker,
    val distance: Double,
    val worldBox: AABB,
    val cameraRelativeBox: AABB,
    val labelPosition: Vec3,
    val color: Color4b,
) {
    val faceColor: Color4b get() = color.with(a = 24)
    val outlineColor: Color4b get() = color.with(a = 150)
    val glowMaskColor: Color4b get() = color.with(a = 255)
}

internal data class BaseFinderRenderLabel(
    val position: Vec3,
    val headline: String,
    val details: String,
    val color: Color4b,
    val evidenceLines: List<String> = emptyList(),
    val scale: Float = 1f,
)

internal data class BaseFinderRenderBatch(
    val entries: List<BaseFinderRenderEntry>,
    val labels: List<BaseFinderRenderLabel>,
) {
    fun contributeGlowIfPresent(contribute: (BaseFinderRenderBatch) -> Unit): Boolean {
        if (entries.isEmpty()) return false
        contribute(this)
        return true
    }

    companion object {
        val EMPTY = BaseFinderRenderBatch(emptyList(), emptyList())
    }
}

private fun List<BaseFinderLabelEvidence>.toRenderEvidence(): List<BaseFinderRenderEvidence> = java.util.List.copyOf(
    map { evidence ->
        BaseFinderRenderEvidence(
            family = evidence.family,
            score = evidence.score,
            detections = java.util.List.copyOf(evidence.detections),
            contributions = java.util.List.copyOf(evidence.contributions.map { contribution ->
                BaseFinderRenderContribution(contribution.label, contribution.score, contribution.observationText)
            }),
            showFamilyScore = evidence.showFamilyScore,
        )
    },
)

internal fun List<BaseFinderRenderEvidence>.immutableRenderCopy(): List<BaseFinderRenderEvidence> =
    java.util.List.copyOf(map { evidence ->
        evidence.copy(
            detections = java.util.List.copyOf(evidence.detections),
            contributions = java.util.List.copyOf(evidence.contributions),
        )
    })
