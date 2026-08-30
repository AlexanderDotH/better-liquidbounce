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
package net.ccbluex.liquidbounce.features.block.placer

import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.minecraft.world.entity.boss.enderdragon.EndCrystal

fun interface CrystalAttackAdapter {
    fun attack(target: EndCrystal, swingMode: SwingMode)
}

object CrystalAttackSink {

    @Volatile
    private var adapter: CrystalAttackAdapter? = null

    @JvmStatic
    @Synchronized
    fun install(adapter: CrystalAttackAdapter) {
        check(this.adapter == null) { "Crystal attack adapter is already installed" }
        this.adapter = adapter
    }

    fun attack(target: EndCrystal, swingMode: SwingMode) {
        checkNotNull(adapter) { "Crystal attack adapter has not been installed" }.attack(target, swingMode)
    }
}
