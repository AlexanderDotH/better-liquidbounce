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
package net.ccbluex.liquidbounce.integration.theme.component.components.trialchamber

import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleTrialChamberTracker
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberRuntime
import net.ccbluex.liquidbounce.features.trialchamber.TrialChamberSnapshot
import net.ccbluex.liquidbounce.features.trialchamber.TrialLootType
import net.ccbluex.liquidbounce.features.trialchamber.TrialSpawnerPhase
import net.ccbluex.liquidbounce.features.trialchamber.TrialVaultStatus
import net.ccbluex.liquidbounce.integration.screen.CustomScreenType
import net.ccbluex.liquidbounce.integration.theme.ThemeManager
import net.ccbluex.liquidbounce.integration.theme.component.isBundledHudRendered
import net.ccbluex.liquidbounce.integration.theme.component.components.NativeHudComponent
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.config.types.group.Alignment

/** Movable native overview for the selected Trial Chamber. */
object TrialChamberHudComponent : NativeHudComponent(
    name = "TrialChamber",
    enabled = true,
    alignment = Alignment(
        horizontalAlignment = TrialChamberHudLayout.HORIZONTAL_ALIGNMENT,
        horizontalOffset = TrialChamberHudLayout.HORIZONTAL_OFFSET,
        verticalAlignment = TrialChamberHudLayout.VERTICAL_ALIGNMENT,
        verticalOffset = TrialChamberHudLayout.VERTICAL_OFFSET,
    ),
    description = "Shows observed Trial Spawner phases, Trial mobs, vault states, and unvisited chamber loot.",
) {

    override val guiScaledWidth = TrialChamberHudLayout.WIDTH
    override val guiScaledHeight = TrialChamberHudLayout.HEIGHT

    init {
        registerComponentListen(this)
    }

    @Suppress("unused")
    private val renderHandler = handler<OverlayRenderEvent> { event ->
        if (HideAppearance.isHidingNow || mc.gui.hud.isHidden) return@handler
        val snapshot = TrialChamberRuntime.snapshot()
        val model = buildTrialChamberHudModel(
            trackerRunning = ModuleTrialChamberTracker.running,
            playerInsideChamber = snapshot?.playerInsideChamber == true,
            currentChamber = snapshot?.toHudInput(),
        ) ?: return@handler

        val bundledHud = isBundledHudRendered()
        val colors = currentTrialChamberHudColors(bundledHud)
        val chrome = resolveTrialChamberHudChrome(
            hudTheme = ModuleHud.theme,
            bundledHud = bundledHud,
            hudAccent = colors.accent,
            classicSurface = colors.classicSurface,
        )
        TrialChamberHudRenderer.render(
            context = event.context,
            bounds = getGuiScaledBounds(),
            chrome = chrome,
            presentation = buildTrialChamberHudPresentation(model),
        )
    }
}

/** Pure defaults kept independent from Minecraft-backed HUD initialization. */
internal object TrialChamberHudLayout {
    const val WIDTH = 240.0F
    const val HEIGHT = 80.0F
    val HORIZONTAL_ALIGNMENT = Alignment.ScreenAxisX.RIGHT
    const val HORIZONTAL_OFFSET = 16
    val VERTICAL_ALIGNMENT = Alignment.ScreenAxisY.TOP
    const val VERTICAL_OFFSET = 16
}

private fun TrialChamberSnapshot.toHudInput() = TrialChamberHudInput(
    spawnerPhases = spawners.map { spawner ->
        when (spawner.phase) {
            TrialSpawnerPhase.INACTIVE -> TrialSpawnerHudPhase.INACTIVE
            TrialSpawnerPhase.WAITING_FOR_PLAYERS -> TrialSpawnerHudPhase.WAITING_FOR_PLAYERS
            TrialSpawnerPhase.ACTIVE -> TrialSpawnerHudPhase.ACTIVE
            TrialSpawnerPhase.WAITING_FOR_REWARD_EJECTION -> TrialSpawnerHudPhase.WAITING_FOR_REWARD_EJECTION
            TrialSpawnerPhase.EJECTING_REWARD -> TrialSpawnerHudPhase.EJECTING_REWARD
            TrialSpawnerPhase.COOLDOWN -> TrialSpawnerHudPhase.COOLDOWN
        }
    },
    trialMobs = mobs.map { mob -> TrialChamberHudMob(isCurrentTrialMob = true, isAlive = mob.alive) },
    vaultStatuses = vaults.map { vault ->
        when (vault.status) {
            TrialVaultStatus.AVAILABLE -> TrialVaultHudStatus.AVAILABLE
            TrialVaultStatus.CLAIMED -> TrialVaultHudStatus.CLAIMED
            TrialVaultStatus.UNKNOWN -> TrialVaultHudStatus.UNKNOWN
        }
    },
    loot = loot.map { resource -> TrialChamberHudLoot(
        type = when (resource.type) {
            TrialLootType.CHEST -> TrialLootHudType.CHEST
            TrialLootType.BARREL -> TrialLootHudType.BARREL
            TrialLootType.POT -> TrialLootHudType.POT
            TrialLootType.DISPENSER -> TrialLootHudType.DISPENSER
        },
        isVisited = resource.visited,
    ) },
)

private fun currentTrialChamberHudColors(bundledHud: Boolean): CurrentTrialChamberHudColors {
    if (!bundledHud) {
        return CurrentTrialChamberHudColors(DEFAULT_TRIAL_CHAMBER_HUD_ACCENT, Color4b.BLACK)
    }

    return runCatching {
        val theme = ThemeManager.getScreenLocation(CustomScreenType.HUD).theme
        val colors = theme.colors.inner.filterIsInstance<Value<*>>()
        val defaultTint = theme.metadata.colors?.get("Tint")?.let(Color4b::fromHex) ?: Color4b.BLACK
        val configuredTint = colors.colorValue("Tint", defaultTint)
        CurrentTrialChamberHudColors(
            accent = colors.colorValue("Accent", DEFAULT_TRIAL_CHAMBER_HUD_ACCENT),
            classicSurface = resolveTrialChamberClassicSurface(defaultTint, configuredTint),
        )
    }.getOrDefault(CurrentTrialChamberHudColors(DEFAULT_TRIAL_CHAMBER_HUD_ACCENT, Color4b.BLACK))
}

private fun List<Value<*>>.colorValue(name: String, fallback: Color4b): Color4b =
    firstOrNull { it.name.equals(name, ignoreCase = true) }?.get() as? Color4b ?: fallback

private data class CurrentTrialChamberHudColors(val accent: Color4b, val classicSurface: Color4b)
