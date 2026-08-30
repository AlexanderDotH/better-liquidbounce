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
package net.ccbluex.liquidbounce.features.module.modules.movement.fly.registry

import net.ccbluex.liquidbounce.config.types.group.Mode
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyAirWalk
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyCreative
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyEnderpearl
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyExplosion
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyJetpack
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyPacket
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.FlyVanilla
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.detector.FlyDetectorBypass
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.fireball.FlyFireball
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.grim.FlyGrim2373Jan15
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.grim.FlyGrim2859V
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.hypixel.FlyHypixel
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.hypixel.FlyHypixelFlat
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.intave.FlyIntave
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.megacraft.FlyMegacraft
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.megacraft.FlyMegacraftNoDown
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.polar.FlyHycraftDamage
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel.FlyCubecraftDamage
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel.FlySentinel10thMar
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel.FlySentinel20thApr
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel.FlySentinel26thDec
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel.FlySentinel27thJan
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentinel.FlySentinelNoDown
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.sentry.FlySentry
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.spartan.FlySpartan524
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.specific.FlyNcpClip
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.vanilla.FlyAntiKickFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.verus.FlyVerusB3869Flat
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.verus.FlyVerusB3896Damage
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.vulcan.FlyVulcan277
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.vulcan.FlyVulcan286
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.vulcan.FlyVulcan286MC18
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.vulcan.FlyVulcan286Teleport

internal fun builtInFlyModes(): Array<Mode> = arrayOf(
    FlyVanilla,
    FlyPacket,
    FlyCreative,
    FlyJetpack,
    FlyEnderpearl,
    FlyAirWalk,
    FlyExplosion,
    FlyFireball,
    FlyDetectorBypass,
    FlyVulcan277,
    FlyVulcan286,
    FlyVulcan286MC18,
    FlyVulcan286Teleport,
    FlyGrim2859V,
    FlyGrim2373Jan15,
    FlySpartan524,
    FlyIntave,
    FlySentinelNoDown,
    FlyCubecraftDamage,
    FlySentinel20thApr,
    FlySentinel27thJan,
    FlySentinel10thMar,
    FlySentinel26thDec,
    FlySentry,
    FlyMegacraft,
    FlyMegacraftNoDown,
    FlyVerusB3896Damage,
    FlyVerusB3869Flat,
    FlyNcpClip,
    FlyAntiKickFly,
    FlyHypixel,
    FlyHypixelFlat,
    FlyHycraftDamage,
)
