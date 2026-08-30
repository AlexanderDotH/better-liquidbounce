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
package net.ccbluex.liquidbounce.features.litematica.application

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActionKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.inventory.Slots
import net.ccbluex.liquidbounce.utils.inventory.findClosestSlot
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

internal class LitematicaMaterialSlotResolver : MinecraftShortcuts {
    fun findSlot(action: LitematicaPrintAction, materialId: String?): HotbarItemSlot? {
        val item = when (action.kind) {
            LitematicaActionKind.BREAK -> return HotbarItemSlot(player.inventory.selectedSlot)
            LitematicaActionKind.FLUID_PICKUP -> Items.BUCKET
            LitematicaActionKind.FLUID_PLACE -> fluidBucket(action.desired.id)
            LitematicaActionKind.PLACE, LitematicaActionKind.AIR_PLACE -> materialId?.let(::registryItem)
        } ?: return null
        return Slots.OffhandWithHotbar.findClosestSlot(item)
    }

    private fun registryItem(id: String): Item? {
        val identifier = Identifier.tryParse(id) ?: return null
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) return null
        return BuiltInRegistries.ITEM.getValue(identifier)
    }

    private fun fluidBucket(fluidId: String): Item? = when (fluidId) {
        "minecraft:water" -> Items.WATER_BUCKET
        "minecraft:lava" -> Items.LAVA_BUCKET
        else -> null
    }
}
