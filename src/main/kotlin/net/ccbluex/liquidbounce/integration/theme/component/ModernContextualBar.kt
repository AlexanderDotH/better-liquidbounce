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

package net.ccbluex.liquidbounce.integration.theme.component

import net.ccbluex.liquidbounce.common.interop.ModernContextualBarSnapshot
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.modules.render.HudTheme
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleHud
import net.ccbluex.liquidbounce.utils.client.mc
import net.minecraft.client.gui.Hud

internal fun resolveModernContextualBarPolicy(
    hudRunning: Boolean,
    appearanceHidden: Boolean,
    hudTheme: HudTheme,
    bundledHud: Boolean,
    hotbarEnabled: Boolean,
): Boolean = hudRunning && !appearanceHidden && hudTheme == HudTheme.MODERN && bundledHud && hotbarEnabled

internal fun resolveContextualInfoForPresentation(
    original: Hud.ContextualInfo,
    disableExperienceBar: Boolean,
    modernContextualBar: Boolean,
): Hud.ContextualInfo {
    if (disableExperienceBar && !modernContextualBar && original == Hud.ContextualInfo.EXPERIENCE) {
        return Hud.ContextualInfo.EMPTY
    }

    return original
}

internal fun normalizeContextualProgress(progress: Float): Float =
    if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f

/**
 * Bridges Minecraft's already-selected contextual state to the bundled browser HUD.
 *
 * The browser never reimplements XP/locator/jump priority. It receives the state cached by
 * [Hud] after `nextContextualInfoState`, then changes only its presentation.
 */
object ModernContextualBar {

    @JvmStatic
    fun shouldRenderInBrowser(): Boolean = resolveModernContextualBarPolicy(
        hudRunning = ModuleHud.running,
        appearanceHidden = HideAppearance.isHidingNow,
        hudTheme = ModuleHud.theme,
        bundledHud = isBundledHudRendered(),
        hotbarEnabled = HudComponentManager.getComponentWithTweak(HudComponentTweak.TWEAK_HOTBAR) != null,
    )

    @JvmStatic
    fun resolveForPresentation(
        original: Hud.ContextualInfo,
        disableExperienceBar: Boolean,
    ): Hud.ContextualInfo = resolveContextualInfoForPresentation(
        original = original,
        disableExperienceBar = disableExperienceBar,
        modernContextualBar = shouldRenderInBrowser(),
    )

    @JvmStatic
    fun snapshot(): ModernContextualBarSnapshot {
        val player = mc.player ?: return ModernContextualBarSnapshot.EMPTY
        val contextualInfo = runCatching {
            (mc.gui.hud as HudContextualInfoAccess).contextualInfoBar.first
        }.getOrDefault(Hud.ContextualInfo.EMPTY)

        return when (contextualInfo) {
            Hud.ContextualInfo.EXPERIENCE -> ModernContextualBarSnapshot(
                mode = MODE_EXPERIENCE,
                progress = normalizeContextualProgress(player.experienceProgress),
                level = player.experienceLevel,
                cooldown = false,
                markers = emptyList(),
            )

            Hud.ContextualInfo.LOCATOR -> ModernContextualBarSnapshot(
                mode = MODE_LOCATOR,
                progress = 0f,
                level = 0,
                cooldown = false,
                markers = ClientPlayerLocatorBar.snapshotMarkers(),
            )

            Hud.ContextualInfo.JUMPABLE_VEHICLE -> {
                val jumpableVehicle = player.jumpableVehicle()
                ModernContextualBarSnapshot(
                    mode = MODE_JUMPABLE_VEHICLE,
                    progress = normalizeContextualProgress(player.jumpRidingScale),
                    level = 0,
                    cooldown = jumpableVehicle?.jumpCooldown?.let { it > 0 } == true,
                    markers = emptyList(),
                )
            }

            Hud.ContextualInfo.EMPTY -> ModernContextualBarSnapshot.EMPTY
        }
    }
}

private const val MODE_EXPERIENCE = "experience"
private const val MODE_LOCATOR = "locator"
private const val MODE_JUMPABLE_VEHICLE = "jumpableVehicle"
