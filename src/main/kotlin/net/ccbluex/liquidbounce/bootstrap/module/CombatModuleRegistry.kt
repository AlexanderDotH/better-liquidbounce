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
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.aimbot.ModuleAutoBow
import net.ccbluex.liquidbounce.features.module.modules.combat.autoarmor.ModuleAutoArmor
import net.ccbluex.liquidbounce.features.module.modules.combat.backtrack.ModuleBacktrack
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.tpaura.ModuleTpAura
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.ModuleVelocity
import net.ccbluex.liquidbounce.features.module.modules.player.autobuff.ModuleAutoBuff

internal val combatModules: Array<ClientModule> = arrayOf(
    ModuleAimbot,
    ModuleAutoArmor,
    ModuleAutoBow,
    ModuleAutoClicker,
    ModuleAutoLeave,
    ModuleAutoBuff,
    ModuleAutoRod,
    ModuleAutoWeapon,
    ModuleFakeLag,
    ModuleCriticals,
    ModuleHitbox,
    ModuleFightBot,
    ModuleKillAura,
    ModuleTpAura,
    ModuleSuperKnockback,
    ModuleTimerRange,
    ModuleTickBase,
    ModuleVelocity,
    ModuleBacktrack,
    ModuleSwordBlock,
    ModuleAutoShoot,
    ModuleKeepSprint,
    ModuleMaceKill,
    ModuleSpearKill,
    ModuleNoMissCooldown,
)
