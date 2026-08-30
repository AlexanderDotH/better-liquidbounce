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
import net.ccbluex.liquidbounce.features.module.modules.combat.elytratarget.ModuleElytraTarget
import net.ccbluex.liquidbounce.features.module.modules.misc.*
import net.ccbluex.liquidbounce.features.module.modules.misc.antibot.ModuleAntiBot
import net.ccbluex.liquidbounce.features.module.modules.misc.betterchat.ModuleBetterChat
import net.ccbluex.liquidbounce.features.module.modules.misc.debugrecorder.ModuleDebugRecorder
import net.ccbluex.liquidbounce.features.module.modules.misc.nameprotect.ModuleNameProtect
import net.ccbluex.liquidbounce.features.module.modules.misc.playercheatdetector.ModulePlayerCheatDetector
import net.ccbluex.liquidbounce.features.module.modules.misc.reporthelper.ModuleReportHelper

internal val miscModules: Array<ClientModule> = arrayOf(
    ModuleAutoConfig,
    ModuleGUICloser,
    ModuleBookBot,
    ModuleAntiBot,
    ModuleBetterTab,
    ModuleItemScroller,
    ModuleBetterChat,
    ModuleElytraTarget,
    ModuleMacros,
    ModuleMiddleClickAction,
    ModuleInventoryTracker,
    ModuleNameProtect,
    ModuleTextFieldProtect,
    ModuleNotifier,
    ModuleSpammer,
    ModuleAutoAccount,
    ModuleTeams,
    ModuleElytraSwap,
    ModuleAutoChatGame,
    ModuleReportHelper,
    ModuleTargetLock,
    ModuleAutoPearl,
    ModuleAntiStaff,
    ModuleFlagCheck,
    ModulePacketLogger,
    ModulePlayerPositionLogger,
    ModuleSafeActions,
    ModuleDebugRecorder,
    ModuleAntiCheatDetect,
    ModulePlayerCheatDetector,
    ModuleEasyPearl,
)
