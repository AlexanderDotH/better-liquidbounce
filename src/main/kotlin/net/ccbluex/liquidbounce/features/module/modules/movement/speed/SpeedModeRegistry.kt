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
package net.ccbluex.liquidbounce.features.module.modules.movement.speed

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedCustom
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedLegitHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedPiercingAttack
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedPulse
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedSpeedYPort
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.SpeedStrafe
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.aac.SpeedAAC332
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.aac.SpeedAAC4310FastHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.aac.SpeedAAC4312LowHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.blockdrop.SpeedBlockdrop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.blockdrop.SpeedBlockdropHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.blocksmc.SpeedBlocksMC
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.detector.SpeedDetectorBypass
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.grim.SpeedGrimCollide
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.gwen.SpeedGWENBHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.gwen.SpeedGWENHighHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.hylex.SpeedHylexGround
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.hylex.SpeedHylexLowHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.intave.SpeedIntave
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.intave.SpeedIntave14
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.intave.SpeedIntave14Fast
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.intave.SpeedIntaveInBlock
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.intave.SpeedIntaveInstant
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.matrix.SpeedMatrix7
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.ncp.SpeedNCP
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.sentinel.SpeedSentinelDamage
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.sentinel.SpeedSentinelFastHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.sentinel.SpeedSentinelLowHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.sentinel.SpeedSentinelOnGround
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.sentinel.SpeedSentinelStrafeHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.spartan.SpeedSpartanV4043
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.spartan.SpeedSpartanV4043FastFall
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.vanilla.SpeedGround
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.venom.SpeedVenomGround
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.verus.SpeedVerusB3882
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.vulcan.SpeedVulcan286
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.vulcan.SpeedVulcan288
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.vulcan.SpeedVulcanGround286
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.watchdog.SpeedHypixelBHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.watchdog.SpeedHypixelLowHop
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.watchdog.SpeedWatchdog

internal fun initializeSpeedModes(parent: ModeValueGroup<*>) = arrayOf(
    SpeedLegitHop(parent), SpeedCustom(parent), SpeedStrafe(parent), SpeedPulse(parent),
    SpeedSpeedYPort(parent), SpeedPiercingAttack(parent), SpeedDetectorBypass(parent),
    SpeedVerusB3882(parent), SpeedHypixelBHop(parent), SpeedHypixelLowHop(parent),
    SpeedSpartanV4043(parent), SpeedSpartanV4043FastFall(parent), SpeedSentinelDamage(parent),
    SpeedVulcan286(parent), SpeedVulcan288(parent), SpeedVulcanGround286(parent), SpeedGrimCollide(parent),
    SpeedNCP(parent), SpeedIntave14(parent), SpeedIntave14Fast(parent), SpeedIntaveInBlock(parent),
    SpeedIntave(parent), SpeedIntaveInstant(parent), SpeedAAC332(parent), SpeedAAC4310FastHop(parent),
    SpeedAAC4312LowHop(parent), SpeedBlockdrop(parent), SpeedBlockdropHop(parent), SpeedGWENBHop(parent),
    SpeedGWENHighHop(parent), SpeedGround(parent), SpeedSentinelFastHop(parent), SpeedSentinelLowHop(parent),
    SpeedSentinelStrafeHop(parent), SpeedSentinelOnGround(parent), SpeedVenomGround(parent), SpeedWatchdog(parent),
    SpeedHylexLowHop(parent), SpeedHylexGround(parent), SpeedBlocksMC(parent), SpeedMatrix7(parent),
)

internal fun speedModeCategory(mode: Mode): String {
    val category = mode.javaClass.packageName.removePrefix(SPEED_MODES_PACKAGE)
        .removePrefix(".").substringBefore('.')
    return when (category) {
        "" -> "General"
        "aac" -> "AAC"
        "blocksmc" -> "BlocksMC"
        "gwen" -> "GWEN"
        "ncp" -> "NCP"
        "watchdog" -> "Hypixel"
        else -> category.replaceFirstChar { it.uppercase() }
    }
}

private const val SPEED_MODES_PACKAGE =
    "net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes"
