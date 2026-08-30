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

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.tick.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.planning.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.delivery.terminal.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.startup.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.recovery.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.integration.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.render.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.SpearKillModuleState
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.SpearKillPreview
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.isUsingSpear
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
