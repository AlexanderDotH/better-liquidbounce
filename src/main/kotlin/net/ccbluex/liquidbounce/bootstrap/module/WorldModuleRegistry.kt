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
import net.ccbluex.liquidbounce.features.module.modules.combat.crystalaura.ModuleCrystalAura
import net.ccbluex.liquidbounce.features.module.modules.world.*
import net.ccbluex.liquidbounce.features.module.modules.world.autobuild.ModuleAutoBuild
import net.ccbluex.liquidbounce.features.module.modules.world.autofarm.ModuleAutoFarm
import net.ccbluex.liquidbounce.features.module.modules.world.automobheal.AutoMobHeal
import net.ccbluex.liquidbounce.features.module.modules.world.basefinder.ModuleBaseFinder
import net.ccbluex.liquidbounce.features.module.modules.world.fucker.ModuleFucker
import net.ccbluex.liquidbounce.features.module.modules.world.nuker.ModuleNuker
import net.ccbluex.liquidbounce.features.module.modules.world.packetmine.ModulePacketMine
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.features.module.modules.world.traps.ModuleAutoTrap

internal val worldModules: Array<ClientModule> = arrayOf(
    AutoMobHeal,
    ModuleAirPlace,
    ModuleAutoBuild,
    ModuleAutoDisable,
    ModuleAutoTimestamp,
    ModuleAutoFarm,
    ModuleAutoTool,
    ModuleCrystalAura,
    ModuleFastBreak,
    ModuleFastPlace,
    ModuleGroundSpoof,
    ModuleFucker,
    ModuleAutoTrap,
    ModuleBlockTrap,
    ModuleNoSlowBreak,
    ModuleLiquidFiller,
    ModuleLiquidPlace,
    ModuleLitematica,
    ModuleProjectilePuncher,
    ModuleScaffold,
    ModuleTimer,
    ModuleNuker,
    ModuleExtinguish,
    ModuleBedDefender,
    ModuleBlockIn,
    ModuleSurround,
    ModulePacketMine,
    ModuleHoleFiller,
    ModuleStrongholdFinder,
    ModuleSeedCracker,
    ModuleBaseFinder,
    ModuleTrialChamberTracker,
    ModuleNoInterpolation,
)
