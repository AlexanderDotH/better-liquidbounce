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
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal object BaseFinderRenderPlanner {
    fun plan(request: BaseFinderRenderRequest): BaseFinderRenderBatch {
        val settings = request.settings
        if (settings.renderLimit <= 0 || settings.maximumDistance < 0.0) return BaseFinderRenderBatch.EMPTY
        val entries = selectEntries(request)
        if (entries.isEmpty()) return BaseFinderRenderBatch.EMPTY
        val labels = createLabels(entries, settings)
        return BaseFinderRenderBatch(java.util.List.copyOf(entries), java.util.List.copyOf(labels))
    }

    private fun selectEntries(request: BaseFinderRenderRequest): List<BaseFinderRenderEntry> {
        val maximumDistanceSq = request.settings.maximumDistance * request.settings.maximumDistance
        return java.util.List.copyOf(request.markers).asSequence()
            .filter { it.belongsTo(request.scope) }
            .filter { it.confidence >= request.settings.minimumConfidence }
            .map { marker -> marker to marker.anchor.distanceToSqr(request.cameraPosition) }
            .filter { (_, distanceSq) -> distanceSq <= maximumDistanceSq }
            .sortedWith(compareBy<Pair<BaseFinderRenderMarker, Double>> { it.second }.thenBy { it.first.id })
            .take(request.settings.renderLimit)
            .map { (marker, distanceSq) -> createEntry(marker, distanceSq, request) }
            .toList()
    }

    private fun createLabels(
        entries: List<BaseFinderRenderEntry>,
        settings: BaseFinderRenderPlanSettings,
    ): List<BaseFinderRenderLabel> = if (settings.showLabels && settings.maxLabels > 0) {
        entries.take(settings.maxLabels).map { createLabel(it, settings) }
    } else {
        emptyList()
    }

    private fun BaseFinderRenderMarker.belongsTo(scope: BaseFinderRenderScope): Boolean =
        serverKey == scope.serverKey && dimensionKey == scope.dimensionKey &&
            worldEpoch == scope.worldEpoch && revision == scope.revision

    private fun createEntry(
        marker: BaseFinderRenderMarker,
        distanceSq: Double,
        request: BaseFinderRenderRequest,
    ): BaseFinderRenderEntry {
        val worldBox = createWorldBox(marker, request.settings)
        return BaseFinderRenderEntry(
            marker = marker.copy(
                topEvidenceKeys = java.util.List.copyOf(marker.topEvidenceKeys),
                evidenceDetails = marker.evidenceDetails.immutableRenderCopy(),
            ),
            distance = sqrt(distanceSq),
            worldBox = worldBox,
            cameraRelativeBox = worldBox.move(request.cameraPosition.reverse()),
            labelPosition = Vec3(
                (worldBox.minX + worldBox.maxX) * 0.5,
                worldBox.maxY + LABEL_VERTICAL_OFFSET,
                (worldBox.minZ + worldBox.maxZ) * 0.5,
            ),
            color = confidenceColor(marker.confidence, request.settings),
        )
    }

    private fun createWorldBox(marker: BaseFinderRenderMarker, settings: BaseFinderRenderPlanSettings): AABB {
        if (settings.boxMode == BaseFinderBoxMode.DYNAMIC) {
            marker.bounds?.let { return createDynamicWorldBox(it, settings.dynamicPadding) }
        }
        return createFixedWorldBox(marker.anchor, settings.boxRadius, settings.boxHeight)
    }

    private fun createFixedWorldBox(anchor: Vec3, radius: Double, height: Double) = AABB(
        anchor.x + 0.5 - radius,
        anchor.y - 0.25,
        anchor.z + 0.5 - radius,
        anchor.x + 0.5 + radius,
        anchor.y + height,
        anchor.z + 0.5 + radius,
    )

    private fun createDynamicWorldBox(bounds: BaseFinderBounds, padding: Int): AABB {
        val margin = padding.coerceAtLeast(0).toDouble()
        return AABB(
            bounds.minimum.x - margin,
            bounds.minimum.y - margin,
            bounds.minimum.z - margin,
            bounds.maximum.x + 1.0 + margin,
            bounds.maximum.y + 1.0 + margin,
            bounds.maximum.z + 1.0 + margin,
        )
    }

    private fun confidenceColor(confidence: Int, settings: BaseFinderRenderPlanSettings): Color4b {
        val minimum = settings.minimumConfidence.coerceIn(0, 100)
        val factor = if (minimum == 100) 1.0 else {
            (confidence.coerceIn(minimum, 100) - minimum).toDouble() / (100 - minimum)
        }
        return settings.lowConfidenceColor.with(a = 255)
            .interpolateTo(settings.highConfidenceColor.with(a = 255), factor)
    }

    private fun createLabel(entry: BaseFinderRenderEntry, settings: BaseFinderRenderPlanSettings): BaseFinderRenderLabel {
        val marker = entry.marker
        val evidence = marker.topEvidenceKeys.take(2).joinToString(" + ").ifEmpty { settings.unknownEvidenceLabel }
        val headline = "${settings.baseLabel} ${marker.confidence.coerceIn(0, 100)}% • " +
            "${entry.distance.roundToInt()}${settings.distanceSuffix}"
        val details = "${marker.anchor.x.toInt()} ${marker.anchor.y.toInt()} ${marker.anchor.z.toInt()} • $evidence"
        val evidenceLines = if (settings.showEvidenceDetails) {
            BaseFinderLabelFormatter.format(marker.evidenceDetails, settings.maxEvidenceDetails)
        } else emptyList()
        return BaseFinderRenderLabel(
            entry.labelPosition, headline, details, entry.outlineColor, evidenceLines,
            settings.labelScale.coerceIn(MINIMUM_LABEL_SCALE, MAXIMUM_LABEL_SCALE),
        )
    }

    private const val MINIMUM_LABEL_SCALE = 0.5f
    private const val MAXIMUM_LABEL_SCALE = 2.5f
    private const val LABEL_VERTICAL_OFFSET = 0.25
}

internal object BaseFinderLabelFormatter {
    fun format(evidence: List<BaseFinderRenderEvidence>, maximumLines: Int): List<String> = evidence
        .asSequence().flatMap(::formatEvidenceLines).take(maximumLines.coerceAtLeast(0)).toList()

    private fun formatEvidenceLines(evidence: BaseFinderRenderEvidence): Sequence<String> {
        if (evidence.contributions.isNotEmpty()) {
            return sequenceOf(formatFamilySubtotal(evidence)) +
                evidence.contributions.asSequence().map(::formatContribution)
        }
        val detections = evidence.detections.joinToString(" + ")
        val subtotal = formatFamilySubtotal(evidence)
        return sequenceOf(if (detections.isEmpty()) subtotal else "$subtotal: $detections")
    }

    private fun formatFamilySubtotal(evidence: BaseFinderRenderEvidence): String =
        if (evidence.showFamilyScore) "${evidence.family} ${formatScore(evidence.score)}" else evidence.family

    private fun formatContribution(contribution: BaseFinderRenderContribution): String {
        val observations = contribution.observationText?.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()
        return "${contribution.label} ${formatScore(contribution.score)}$observations"
    }

    private fun formatScore(score: Int): String = if (score > 0) "+$score" else score.toString()
}
