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
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.world

import net.ccbluex.liquidbounce.config.types.group.ValueGroup
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderFilters
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderPlan
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderPlanCache
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderSnapshotKey
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderSettings
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderTarget
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.SnapshotRenderTargetMapper
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberRuntime
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberSnapshot
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.WorldToScreen
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Visual tracker for the chamber selected by the always-on [TrialChamberRuntime]. */
object ModuleTrialChamberTracker : ClientModule(
    "TrialChamberTracker",
    ModuleCategories.WORLD,
) {

    private val maximumDistance by int("MaximumDistance", 192, 32..192, "blocks")
    private val glow by boolean("Glow", true)
    private val glowStyle = EspGlowStyleConfig(this)
    private val showVisited by boolean("ShowVisited", false)
    private val showCompleted by boolean("ShowCompleted", false)

    private object Filters : ValueGroup("Filters") {
        val spawners by boolean("Spawners", true)
        val normalVaults by boolean("NormalVaults", true)
        val ominousVaults by boolean("OminousVaults", true)
        val chests by boolean("Chests", true)
        val barrels by boolean("Barrels", true)
        val pots by boolean("Pots", true)
        val dispensers by boolean("Dispensers", true)
    }

    private object Labels : ValueGroup("Labels") {
        val show by boolean("Show", true)
        val maximum by int("Maximum", 24, 1..24)
    }

    private val renderPlan = AtomicReference(TrialChamberRenderPlan.EMPTY)
    private val renderPlanCache = TrialChamberRenderPlanCache()
    private var cachedRenderTargetKey: TrialChamberRenderSnapshotKey? = null
    private var cachedRenderTargets: List<TrialChamberRenderTarget> = emptyList()

    init {
        tree(Filters)
        tree(Labels)
    }

    override fun onEnabled() {
        clearRenderState()
        TrialChamberRuntime.setResourceTrackingEnabled(true)
    }

    override fun onDisabled() {
        TrialChamberRuntime.setResourceTrackingEnabled(false)
        clearRenderState()
    }

    @Suppress("unused")
    private val worldRenderHandler = handler<WorldRenderEvent> { event ->
        val snapshot = TrialChamberRuntime.snapshot()
        if (snapshot == null) {
            clearRenderState()
            return@handler
        }

        if (!glow && !Labels.show) {
            renderPlan.set(TrialChamberRenderPlan.EMPTY)
            return@handler
        }

        val cameraPosition = event.camera.position()
        val snapshotKey = TrialChamberRenderSnapshotKey(snapshot.worldEpoch, snapshot.revision)
        val plan = renderPlanCache.resolve(
            key = snapshotKey,
            cameraPosition = cameraPosition,
            targets = renderTargets(snapshot, snapshotKey),
            settings = currentRenderSettings(),
        )
        renderPlan.set(plan)
        if (!glow || plan.glowBoxes.isEmpty()) return@handler

        val cameraOffset = cameraPosition.reverse()
        EspShaderRenderer.contributeGlow(event, EspGlowSource.TRIAL_CHAMBER, glowStyle.style) {
            plan.glowBoxes.forEach { box ->
                drawBox(box.worldBox.move(cameraOffset), box.glowMaskColor, null)
            }
        }
    }

    @Suppress("unused")
    private val overlayRenderHandler = handler<OverlayRenderEvent> { event ->
        if (!Labels.show) return@handler
        val width = mc.window.guiScaledWidth.toFloat()
        val height = mc.window.guiScaledHeight.toFloat()
        renderPlan.get().labels.forEach { label ->
            val screen = WorldToScreen.calculateScreenPos(label.position) ?: return@forEach
            if (screen.x !in 0.0F..width || screen.y !in 0.0F..height) return@forEach
            val x = (screen.x - mc.font.width(label.text) * 0.5F).roundToInt()
            event.context.text(mc.font, label.text, x, screen.y.roundToInt(), label.color.argb, true)
        }
    }

    private fun currentRenderSettings() = TrialChamberRenderSettings(
        maximumDistance = maximumDistance.toDouble(),
        showGlow = glow,
        showLabels = Labels.show,
        maximumLabels = Labels.maximum,
        showVisited = showVisited,
        showCompleted = showCompleted,
        filters = TrialChamberRenderFilters(
            spawners = Filters.spawners,
            normalVaults = Filters.normalVaults,
            ominousVaults = Filters.ominousVaults,
            chests = Filters.chests,
            barrels = Filters.barrels,
            pots = Filters.pots,
            dispensers = Filters.dispensers,
        ),
    )

    private fun renderTargets(
        snapshot: TrialChamberSnapshot,
        key: TrialChamberRenderSnapshotKey,
    ): List<TrialChamberRenderTarget> {
        if (key == cachedRenderTargetKey) return cachedRenderTargets
        cachedRenderTargetKey = key
        cachedRenderTargets = SnapshotRenderTargetMapper.map(snapshot)
        return cachedRenderTargets
    }

    private fun clearRenderState() {
        renderPlan.set(TrialChamberRenderPlan.EMPTY)
        renderPlanCache.reset()
        cachedRenderTargetKey = null
        cachedRenderTargets = emptyList()
    }
}
