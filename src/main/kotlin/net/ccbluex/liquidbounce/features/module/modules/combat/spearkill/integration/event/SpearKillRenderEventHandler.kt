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
package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.preview.SpearKillPreview
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.orchestration.session.isUsingSpear
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.SpearKillAStarPathAppearance
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.renderSpearKillAStarPath
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.shouldRenderSpearKillAStarPath
import net.ccbluex.liquidbounce.render.drawBox
import net.ccbluex.liquidbounce.render.renderEnvironment
import net.ccbluex.liquidbounce.render.withPositionRelativeToCamera
import net.minecraft.world.entity.boss.enderdragon.EnderDragon

internal fun SpearKillModuleState.registerRenderHandler() {
    handler<WorldRenderEvent> { event -> renderSpearKillPreview(event) }
}

private fun SpearKillModuleState.renderSpearKillPreview(event: WorldRenderEvent) {
    renderSpearKillPathPreview(event)
    if (!SpearKillPreview.enabled ||
        SpearKillPreview.mode.activeMode !== SpearKillPreview.Box ||
        !isUsingSpear
    ) {
        return
    }
    val target = previewTarget ?: return
    event.renderEnvironment {
        withPositionRelativeToCamera {
            if (target is EnderDragon) {
                target.subEntities.forEach {
                    drawBox(it.boundingBox, SpearKillPreview.Box.fillColor, SpearKillPreview.Box.outlineColor)
                }
            } else {
                drawBox(target.boundingBox, SpearKillPreview.Box.fillColor, SpearKillPreview.Box.outlineColor)
            }
        }
    }
}

private fun SpearKillModuleState.renderSpearKillPathPreview(event: WorldRenderEvent) {
    if (!shouldRenderSpearKillAStarPath(
            SpearKillPreview.enabled,
            packetAStarAttackActive,
            SpearKillPreview.renderPath,
            plannedAStarRenderPath,
        )
    ) {
        return
    }
    renderSpearKillAStarPath(
        event,
        plannedAStarRenderPath,
        SpearKillAStarPathAppearance(
            SpearKillPreview.Glow.glowColor,
            SpearKillPreview.Glow.glowStyle.style,
        ),
    )
}
