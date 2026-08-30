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
package net.ccbluex.liquidbounce.features.module.modules.render.esp.integration

import net.ccbluex.liquidbounce.common.EspMaskLayer
import net.ccbluex.liquidbounce.common.EspMaskRequest
import net.ccbluex.liquidbounce.features.combat.runtime.EntityTaggingManager
import net.ccbluex.liquidbounce.features.combat.runtime.shouldBeShown
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleItemESP
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleOrbESP
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleStorageESP
import net.ccbluex.liquidbounce.features.module.modules.render.esp.ModuleESP
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspChamsMode
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspGlowMode
import net.ccbluex.liquidbounce.features.module.modules.render.esp.modes.EspOutlineMode
import net.ccbluex.liquidbounce.features.module.modules.render.esp.runtime.EspModeRuntime
import net.ccbluex.liquidbounce.render.engine.esp.EspMaskFeatureSelector
import net.ccbluex.liquidbounce.render.engine.esp.TargetGlowSourceRegistry
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntity

object EspMaskFeatureAdapter : EspMaskFeatureSelector {

    internal fun installCombatPresentation() {
        EspModeRuntime.installCombatPresentation(
            taggedColor = { entity -> EntityTaggingManager.getTag(entity).color },
            shouldBeShown = { entity -> entity.shouldBeShown() },
        )
    }

    override fun forEntity(entity: Entity?): EspMaskRequest {
        if (entity == null) return EspMaskRequest.NONE

        var request = protectPlayerSurface(entity)
        request = appendTargetGlow(request, entity)
        request = appendLivingEntityMask(request, entity)
        request = appendItemMask(request, entity)
        request = appendOrbMask(request, entity)
        return appendStorageEntityMask(request, entity)
    }

    override fun forBlockEntity(blockEntity: BlockEntity?): EspMaskRequest {
        if (blockEntity == null) return EspMaskRequest.NONE

        val category = ModuleStorageESP.run { blockEntity.categorize() } ?: return EspMaskRequest.NONE
        if (!category.shouldRender(blockEntity.blockPos) || category.color.isTransparent) {
            return EspMaskRequest.NONE
        }

        val layer = activeStorageLayer() ?: return EspMaskRequest.NONE.with(
            EspMaskLayer.PROTECTED_SURFACE,
            PROTECTED_SURFACE_COLOR,
        )
        return appendProtectedMask(EspMaskRequest.NONE, layer, category.color.argb)
    }

    private fun protectPlayerSurface(entity: Entity): EspMaskRequest =
        if (entity is Player) {
            EspMaskRequest.NONE.with(EspMaskLayer.PROTECTED_SURFACE, PROTECTED_SURFACE_COLOR)
        } else {
            EspMaskRequest.NONE
        }

    private fun appendTargetGlow(request: EspMaskRequest, entity: Entity): EspMaskRequest {
        val selection = TargetGlowSourceRegistry.selectionFor(entity) ?: return request
        return request.with(EspMaskLayer.TARGET_GLOW, selection.color.argb)
    }

    private fun appendLivingEntityMask(request: EspMaskRequest, entity: Entity): EspMaskRequest {
        if (entity !is LivingEntity || !entity.shouldBeShown()) return request
        val color = ModuleESP.getColor(entity).argb
        val layer = selectEntityMaskLayer(
            chams = { EspChamsMode.running && EspChamsMode.shouldRender(entity) },
            glow = { EspGlowMode.running && EspGlowMode.shouldRender(entity) },
            outline = { EspOutlineMode.running && EspOutlineMode.shouldRender(entity) },
        ) ?: return request
        return request.with(layer, color)
    }

    private fun appendItemMask(request: EspMaskRequest, entity: Entity): EspMaskRequest {
        if (!ModuleItemESP.ShaderEspMode.running || !ModuleItemESP.shouldRender(entity)) return request
        return appendProtectedMask(request, EspMaskLayer.ITEM_GLOW, ModuleItemESP.getColor().argb)
    }

    private fun appendOrbMask(request: EspMaskRequest, entity: Entity): EspMaskRequest {
        if (entity !is ExperienceOrb || !ModuleOrbESP.GlowMode.running || !ModuleOrbESP.shouldRender(entity)) {
            return request
        }
        return appendProtectedMask(request, EspMaskLayer.ORB_GLOW, ModuleOrbESP.getColor().argb)
    }

    private fun appendStorageEntityMask(request: EspMaskRequest, entity: Entity): EspMaskRequest {
        val category = ModuleStorageESP.run { entity.categorize() } ?: return request
        if (!category.shouldRender(entity) || category.color.isTransparent) return request
        val layer = activeStorageLayer() ?: return request.with(
            EspMaskLayer.PROTECTED_SURFACE,
            PROTECTED_SURFACE_COLOR,
        )
        return appendProtectedMask(request, layer, category.color.argb)
    }

    private fun activeStorageLayer(): EspMaskLayer? = selectStorageMaskLayer(
        chams = { ModuleStorageESP.ChamsMode.running },
        glow = { ModuleStorageESP.GlowMode.running },
        outline = { ModuleStorageESP.OutlineMode.running },
    )

    private const val PROTECTED_SURFACE_COLOR = -1
}
