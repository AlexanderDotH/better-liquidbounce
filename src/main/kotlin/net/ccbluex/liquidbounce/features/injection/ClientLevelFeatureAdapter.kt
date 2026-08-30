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

package net.ccbluex.liquidbounce.features.injection

import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleNoPush
import net.ccbluex.liquidbounce.features.module.modules.movement.NoPushBy
import net.ccbluex.liquidbounce.features.module.modules.render.DoRender
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleAntiBlind
import net.ccbluex.liquidbounce.interfaces.ClientLevelFeatureBridge
import net.ccbluex.liquidbounce.interfaces.ClientLevelFeatureProvider

object ClientLevelFeatureAdapter : ClientLevelFeatureProvider {
    fun install() = ClientLevelFeatureBridge.install(this)

    override fun canRenderExplosionParticles(): Boolean = ModuleAntiBlind.canRender(DoRender.EXPLOSION_PARTICLES)

    override fun canRenderBlockBreakParticles(): Boolean = ModuleAntiBlind.canRender(DoRender.BLOCK_BREAK_PARTICLES)

    override fun canPushEntities(): Boolean = ModuleNoPush.canPush(NoPushBy.ENTITIES)

    override fun canPushFishingRod(): Boolean = ModuleNoPush.canPush(NoPushBy.FISHING_ROD)
}
