/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.bootstrap.module

import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.player.*
import net.ccbluex.liquidbounce.features.module.modules.player.antivoid.ModuleAntiVoid
import net.ccbluex.liquidbounce.features.module.modules.player.autoqueue.ModuleAutoQueue
import net.ccbluex.liquidbounce.features.module.modules.player.autoshop.ModuleAutoShop
import net.ccbluex.liquidbounce.features.module.modules.player.cheststealer.ModuleChestStealer
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ModuleInventoryCleaner
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.ccbluex.liquidbounce.features.module.modules.player.offhand.ModuleOffhand

internal val playerModules: Array<ClientModule> = arrayOf(
    ModuleAntiVoid,
    ModuleAntiAFK,
    ModuleAntiExploit,
    ModuleAutoBreak,
    ModuleAutoCrafter,
    ModuleAutoFish,
    ModuleAutoRespawn,
    ModuleAutoWindCharge,
    ModuleOffhand,
    ModuleAutoShop,
    ModuleAutoWalk,
    ModuleBaritone,
    ModuleBlink,
    ModuleChestCleaner,
    ModuleChestStealer,
    ModuleEagle,
    ModuleFastExp,
    ModuleFastUse,
    ModuleInventoryCleaner,
    ModuleNoBlockInteract,
    ModuleNoCapability,
    ModuleNoEntityInteract,
    ModuleNoFall,
    ModuleNoRotateSet,
    ModuleNoSlotSet,
    ModuleReach,
    ModuleAutoQueue,
    ModuleSmartEat,
    ModuleReplenish,
    ModulePotionSpoof,
)
