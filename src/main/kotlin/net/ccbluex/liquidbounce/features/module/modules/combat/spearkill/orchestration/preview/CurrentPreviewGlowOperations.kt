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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.preview

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isUsingSpear

import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSelection

internal fun SpearKillModuleState.currentPreviewGlow(): TargetGlowSelection? {
    if (!enabled || !SpearKillPreview.enabled ||
        SpearKillPreview.mode.activeMode !== SpearKillPreview.Glow || !isUsingSpear
    ) {
        return null
    }
    val target = previewTarget ?: return null
    return TargetGlowSelection(
        target,
        SpearKillPreview.Glow.glowColor,
        SpearKillPreview.Glow.glowStyle.style,
    )
}
