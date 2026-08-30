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
package net.ccbluex.liquidbounce.render.engine.esp

import net.ccbluex.liquidbounce.common.EspMaskRequest
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity

interface EspMaskFeatureSelector {
    fun forEntity(entity: Entity?): EspMaskRequest
    fun forBlockEntity(blockEntity: BlockEntity?): EspMaskRequest
}

object EspMaskFeatureSelectorRegistry {

    @Volatile
    private var selector: EspMaskFeatureSelector? = null

    @JvmStatic
    @Synchronized
    fun install(selector: EspMaskFeatureSelector) {
        check(this.selector == null) { "ESP mask feature selector is already installed" }
        this.selector = selector
    }

    @JvmStatic
    fun forEntity(entity: Entity?): EspMaskRequest = selector?.forEntity(entity) ?: EspMaskRequest.NONE

    @JvmStatic
    fun forBlockEntity(blockEntity: BlockEntity?): EspMaskRequest =
        selector?.forBlockEntity(blockEntity) ?: EspMaskRequest.NONE

    @Synchronized
    internal fun <T> withSelectorForTest(candidate: EspMaskFeatureSelector?, block: () -> T): T {
        val previous = selector
        selector = candidate
        return try {
            block()
        } finally {
            selector = previous
        }
    }
}
