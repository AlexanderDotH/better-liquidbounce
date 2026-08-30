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

import net.ccbluex.liquidbounce.features.combat.runtime.CombatManager
import net.ccbluex.liquidbounce.features.misc.HideAppearance
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.features.KillAuraAutoBlock
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.ReachInteractableFeature
import net.ccbluex.liquidbounce.features.module.modules.render.playermodel.ServerPlayerModelStateTracker
import net.ccbluex.liquidbounce.interfaces.MinecraftClientFeatureBridge
import net.ccbluex.liquidbounce.interfaces.MinecraftClientFeatureProvider

object MinecraftClientFeatureAdapter : MinecraftClientFeatureProvider {
    fun install() = MinecraftClientFeatureBridge.install(this)

    override fun isAppearanceHidden(): Boolean = HideAppearance.isHidingNow

    override fun onGameTick() = ServerPlayerModelStateTracker.onGameTick()

    override fun claimReachUse(): Boolean = ReachInteractableFeature.claimUse()

    override fun hasEnforcedBlockingHand(): Boolean =
        KillAuraAutoBlock.running && KillAuraAutoBlock.enforcedBlockingHand != null

    override fun shouldPauseCombat(): Boolean = CombatManager.shouldPauseCombat

    override fun resetPlayerModelState() = ServerPlayerModelStateTracker.reset()
}
