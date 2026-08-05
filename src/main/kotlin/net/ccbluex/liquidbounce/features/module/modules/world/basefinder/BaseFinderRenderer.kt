/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package net.ccbluex.liquidbounce.features.module.modules.world.basefinder

import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyle
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.utils.client.mc
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Identifies the world whose immutable findings are allowed to reach the renderer. */
internal data class BaseFinderRenderScope(
    val serverKey: String,
    val dimensionKey: String,
    val worldEpoch: Long,
    val revision: Long = 0L,
)

/** Render-only projection of a persisted or newly accepted base finding. */
internal data class BaseFinderRenderMarker(
    val id: String,
    val serverKey: String,
    val dimensionKey: String,
    val worldEpoch: Long,
    val anchor: Vec3,
    val confidence: Int,
    val topEvidenceKeys: List<String>,
    val updatedAtMillis: Long,
    val revision: Long = 0L,
)

/** Values owned by ModuleBaseFinder and copied before planning a render frame. */
internal data class BaseFinderRenderSettings(
    val minimumConfidence: Int,
    val maximumDistance: Double,
    val renderLimit: Int,
    val boxRadius: Double,
    val boxHeight: Double,
    val lowConfidenceColor: Color4b,
    val highConfidenceColor: Color4b,
    val showLabels: Boolean,
    val maxLabels: Int,
    val pulse: Boolean,
    val pulseSpeedHz: Double,
    val pulseAmount: Double,
    val baseLabel: String = "Base",
    val unknownEvidenceLabel: String = "Unknown",
    val distanceSuffix: String = "m",
)

/** All mutable client state is reduced to this request before the pure planning step. */
internal data class BaseFinderRenderRequest(
    val scope: BaseFinderRenderScope,
    val cameraPosition: Vec3,
    val settings: BaseFinderRenderSettings,
    val markers: List<BaseFinderRenderMarker>,
    val nowMillis: Long,
) {
    companion object {
        fun fromSnapshot(
            snapshot: BaseFinderRenderSnapshot,
            cameraPosition: Vec3,
            settings: BaseFinderRenderSettings,
            nowMillis: Long,
        ): BaseFinderRenderRequest {
            val scope = BaseFinderRenderScope(
                snapshot.serverKey,
                snapshot.dimensionKey,
                snapshot.worldEpoch,
                snapshot.revision,
            )
            val markers = java.util.List.copyOf(snapshot.markers).map { marker ->
                BaseFinderRenderMarker(
                    id = marker.id,
                    serverKey = snapshot.serverKey,
                    dimensionKey = snapshot.dimensionKey,
                    worldEpoch = snapshot.worldEpoch,
                    anchor = Vec3(
                        marker.anchor.x.toDouble(),
                        marker.anchor.y.toDouble(),
                        marker.anchor.z.toDouble(),
                    ),
                    confidence = marker.confidence,
                    topEvidenceKeys = java.util.List.copyOf(marker.topEvidenceKeys),
                    updatedAtMillis = marker.updatedAtMillis,
                    revision = snapshot.revision,
                )
            }
            return BaseFinderRenderRequest(
                scope,
                cameraPosition,
                settings,
                java.util.List.copyOf(markers),
                nowMillis,
            )
        }
    }
}

internal data class BaseFinderRenderEntry(
    val marker: BaseFinderRenderMarker,
    val distance: Double,
    val worldBox: AABB,
    val cameraRelativeBox: AABB,
    val labelPosition: Vec3,
    val color: Color4b,
    val pulseMultiplier: Double,
) {
    val faceColor: Color4b
        get() = color.with(a = (FACE_ALPHA * pulseMultiplier).roundToInt().coerceIn(0, 255))

    val outlineColor: Color4b
        get() = color.with(a = (OUTLINE_ALPHA * pulseMultiplier).roundToInt().coerceIn(0, 255))

    val glowMaskColor: Color4b
        get() = color.with(a = (FULL_ALPHA * pulseMultiplier).roundToInt().coerceIn(0, 255))

    companion object {
        private const val FACE_ALPHA = 24
        private const val OUTLINE_ALPHA = 150
        private const val FULL_ALPHA = 255
    }
}

internal data class BaseFinderRenderLabel(
    val position: Vec3,
    val headline: String,
    val details: String,
    val color: Color4b,
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

/** Pure filtering and geometry planner; safe to unit-test without a render context. */
internal object BaseFinderRenderPlanner {

    fun plan(request: BaseFinderRenderRequest): BaseFinderRenderBatch {
        val settings = request.settings
        if (settings.renderLimit <= 0 || settings.maximumDistance < 0.0) return BaseFinderRenderBatch.EMPTY

        val maximumDistanceSq = settings.maximumDistance * settings.maximumDistance
        val markerSnapshot = java.util.List.copyOf(request.markers)
        val entries = markerSnapshot.asSequence()
            .filter { it.belongsTo(request.scope) }
            .filter { it.confidence >= settings.minimumConfidence }
            .map { marker -> marker to marker.anchor.distanceToSqr(request.cameraPosition) }
            .filter { (_, distanceSq) -> distanceSq <= maximumDistanceSq }
            .sortedWith(compareBy<Pair<BaseFinderRenderMarker, Double>> { it.second }.thenBy { it.first.id })
            .take(settings.renderLimit)
            .map { (marker, distanceSq) -> createEntry(marker, distanceSq, request) }
            .toList()

        if (entries.isEmpty()) return BaseFinderRenderBatch.EMPTY

        val labels = if (settings.showLabels && settings.maxLabels > 0) {
            entries.take(settings.maxLabels).map { createLabel(it, settings) }
        } else {
            emptyList()
        }
        return BaseFinderRenderBatch(java.util.List.copyOf(entries), java.util.List.copyOf(labels))
    }

    internal fun pulseMultiplier(id: String, nowMillis: Long, speedHz: Double, amount: Double): Double {
        val boundedAmount = amount.coerceIn(0.0, 1.0)
        if (boundedAmount == 0.0) return 1.0

        val cycle = nowMillis / 1_000.0 * speedHz.coerceAtLeast(0.0) * TWO_PI
        val phase = (id.hashCode().toLong() and UNSIGNED_INT_MASK) / UNSIGNED_INT_MAX * TWO_PI
        val wave = 0.5 + 0.5 * sin(cycle + phase)
        return 1.0 - boundedAmount * wave
    }

    private fun BaseFinderRenderMarker.belongsTo(scope: BaseFinderRenderScope): Boolean {
        return serverKey == scope.serverKey &&
            dimensionKey == scope.dimensionKey &&
            worldEpoch == scope.worldEpoch &&
            revision == scope.revision
    }

    private fun createEntry(
        marker: BaseFinderRenderMarker,
        distanceSq: Double,
        request: BaseFinderRenderRequest,
    ): BaseFinderRenderEntry {
        val settings = request.settings
        val worldBox = createWorldBox(marker.anchor, settings.boxRadius, settings.boxHeight)
        val pulseMultiplier = if (settings.pulse) {
            pulseMultiplier(marker.id, request.nowMillis, settings.pulseSpeedHz, settings.pulseAmount)
        } else {
            1.0
        }
        return BaseFinderRenderEntry(
            marker = marker.copy(topEvidenceKeys = java.util.List.copyOf(marker.topEvidenceKeys)),
            distance = sqrt(distanceSq),
            worldBox = worldBox,
            cameraRelativeBox = worldBox.move(request.cameraPosition.reverse()),
            labelPosition = Vec3(
                marker.anchor.x + 0.5,
                marker.anchor.y + settings.boxHeight + 0.25,
                marker.anchor.z + 0.5,
            ),
            color = confidenceColor(marker.confidence, settings),
            pulseMultiplier = pulseMultiplier,
        )
    }

    private fun createWorldBox(anchor: Vec3, radius: Double, height: Double): AABB {
        return AABB(
            anchor.x + 0.5 - radius,
            anchor.y - 0.25,
            anchor.z + 0.5 - radius,
            anchor.x + 0.5 + radius,
            anchor.y + height,
            anchor.z + 0.5 + radius,
        )
    }

    private fun confidenceColor(confidence: Int, settings: BaseFinderRenderSettings): Color4b {
        val minimum = settings.minimumConfidence.coerceIn(0, 100)
        val factor = if (minimum == 100) {
            1.0
        } else {
            (confidence.coerceIn(minimum, 100) - minimum).toDouble() / (100 - minimum)
        }
        return settings.lowConfidenceColor.with(a = 255)
            .interpolateTo(settings.highConfidenceColor.with(a = 255), factor)
    }

    private fun createLabel(
        entry: BaseFinderRenderEntry,
        settings: BaseFinderRenderSettings,
    ): BaseFinderRenderLabel {
        val marker = entry.marker
        val evidence = marker.topEvidenceKeys.take(2).joinToString(" + ").ifEmpty { settings.unknownEvidenceLabel }
        val headline = "${settings.baseLabel} ${marker.confidence.coerceIn(0, 100)}% • " +
            "${entry.distance.roundToInt()}${settings.distanceSuffix}"
        val details = "${marker.anchor.x.toInt()} ${marker.anchor.y.toInt()} " +
            "${marker.anchor.z.toInt()} • $evidence"
        return BaseFinderRenderLabel(entry.labelPosition, headline, details, entry.outlineColor)
    }

    private const val TWO_PI = PI * 2.0
    private const val UNSIGNED_INT_MASK = 0xffffffffL
    private const val UNSIGNED_INT_MAX = 0xffffffffL.toDouble()
}

/** Thin adapter that submits one immutable batch to direct, glow, and overlay render APIs. */
internal object BaseFinderRenderer {

    fun renderWorld(event: WorldRenderEvent, batch: BaseFinderRenderBatch, glowStyle: EspGlowStyle) {
        if (batch.entries.isEmpty()) return

        event.renderEnvironment {
            for (entry in batch.entries) {
                drawBox(entry.cameraRelativeBox, entry.faceColor, entry.outlineColor)
            }
        }
        batch.contributeGlowIfPresent {
            EspShaderRenderer.contributeGlow(event, glowStyle) {
                for (entry in it.entries) {
                    drawBox(entry.cameraRelativeBox, entry.glowMaskColor, null)
                }
            }
        }
    }

    fun renderLabels(event: OverlayRenderEvent, batch: BaseFinderRenderBatch) {
        val width = mc.window.guiScaledWidth.toFloat()
        val height = mc.window.guiScaledHeight.toFloat()
        for (label in batch.labels) {
            val screen = WorldToScreen.calculateScreenPos(label.position) ?: continue
            if (screen.x !in 0f..width || screen.y !in 0f..height) continue

            val headlineX = (screen.x - mc.font.width(label.headline) * 0.5f).roundToInt()
            val detailsX = (screen.x - mc.font.width(label.details) * 0.5f).roundToInt()
            val headlineY = screen.y.roundToInt()
            event.context.text(mc.font, label.headline, headlineX, headlineY, label.color.argb, true)
            event.context.text(
                mc.font,
                label.details,
                detailsX,
                headlineY + mc.font.lineHeight + 1,
                Color4b.WHITE.argb,
                true,
            )
        }
    }
}
