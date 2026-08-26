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
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderFilters
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderPlan
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderPlanCache
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderSnapshotKey
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderSettings
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderTarget
import net.ccbluex.liquidbounce.features.module.modules.world.trialchamber.TrialChamberRenderTargetKind
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberPosition
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberRuntime
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberSnapshot
import net.ccbluex.liquidbounce.features.trialchamber.TrialLootType
import net.ccbluex.liquidbounce.features.trialchamber.TrialSpawnerPhase
import net.ccbluex.liquidbounce.features.trialchamber.TrialVaultStatus
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowSource
import net.ccbluex.liquidbounce.render.engine.esp.EspGlowStyleConfig
import net.ccbluex.liquidbounce.render.engine.esp.EspShaderRenderer
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
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
        cachedRenderTargets = snapshot.renderTargets()
        return cachedRenderTargets
    }

    private fun clearRenderState() {
        renderPlan.set(TrialChamberRenderPlan.EMPTY)
        renderPlanCache.reset()
        cachedRenderTargetKey = null
        cachedRenderTargets = emptyList()
    }
}

private fun TrialChamberSnapshot.renderTargets(): List<TrialChamberRenderTarget> = buildList {
    spawners.forEach { spawner ->
        add(TrialChamberRenderTarget(
            id = "spawner:${spawner.position.id}",
            kind = TrialChamberRenderTargetKind.SPAWNER,
            position = spawner.position.center,
            worldBox = spawner.position.blockBox,
            label = "Trial Spawner: ${spawner.phase.displayName}",
            color = SPAWNER_COLOR,
            completed = spawner.completed,
        ))
    }
    vaults.forEach { vault ->
        add(TrialChamberRenderTarget(
            id = "vault:${vault.position.id}",
            kind = if (vault.ominous) {
                TrialChamberRenderTargetKind.OMINOUS_VAULT
            } else {
                TrialChamberRenderTargetKind.NORMAL_VAULT
            },
            position = vault.position.center,
            worldBox = vault.position.blockBox,
            label = "${if (vault.ominous) "Ominous " else ""}Vault: ${vault.status.displayName}",
            color = if (vault.ominous) OMINOUS_VAULT_COLOR else VAULT_COLOR,
            completed = vault.completed,
        ))
    }
    loot.forEach { resource ->
        val (kind, label, color) = when (resource.type) {
            TrialLootType.CHEST -> Triple(TrialChamberRenderTargetKind.CHEST, "Chest", CHEST_COLOR)
            TrialLootType.BARREL -> Triple(TrialChamberRenderTargetKind.BARREL, "Barrel", BARREL_COLOR)
            TrialLootType.POT -> Triple(TrialChamberRenderTargetKind.POT, "Pot", POT_COLOR)
            TrialLootType.DISPENSER -> Triple(TrialChamberRenderTargetKind.DISPENSER, "Dispenser", DISPENSER_COLOR)
        }
        add(TrialChamberRenderTarget(
            id = "${resource.type.name.lowercase()}:${resource.position.id}",
            kind = kind,
            position = resource.position.center,
            worldBox = resource.position.blockBox,
            label = label,
            color = color,
            visited = resource.visited,
        ))
    }
}

private val TrialChamberPosition.id: String
    get() = "$x,$y,$z"

private val TrialChamberPosition.center: Vec3
    get() = Vec3(x + 0.5, y + 0.5, z + 0.5)

private val TrialChamberPosition.blockBox: AABB
    get() = AABB(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)

private val TrialSpawnerPhase.displayName: String
    get() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private val TrialVaultStatus.displayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private val SPAWNER_COLOR = Color4b(255, 132, 48)
private val VAULT_COLOR = Color4b(55, 210, 255)
private val OMINOUS_VAULT_COLOR = Color4b(165, 92, 255)
private val CHEST_COLOR = Color4b(40, 130, 255)
private val BARREL_COLOR = Color4b(246, 130, 31)
private val POT_COLOR = Color4b(224, 166, 45)
private val DISPENSER_COLOR = Color4b(190, 190, 190)
