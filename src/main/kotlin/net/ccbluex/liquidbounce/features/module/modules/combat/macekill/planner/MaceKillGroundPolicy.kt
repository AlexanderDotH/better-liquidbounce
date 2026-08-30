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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner

import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.facade.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*

import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

internal enum class MaceKillGroundPolicy {
    COLLISION_DERIVED,
    CLIP_ANCHOR_SPOOF,
    ;

    fun shouldSpoofOnGround(packet: MaceKillGroundPacketContext): Boolean =
        this == CLIP_ANCHOR_SPOOF &&
            packet.identityOwnedByRoute &&
            (packet.kind == MaceKillMovementPacketKind.CLIP_REACH_ANCHOR ||
                packet.kind == MaceKillMovementPacketKind.CLIP_REACH_RECOVERY)
}

internal enum class MaceKillMovementPacketKind {
    DIRECT_ROUTE,
    ASTAR_ROUTE,
    VANILLA_VCLIP,
    CLIP_REACH_ANCHOR,
    CLIP_REACH_RECOVERY,
    INSTANT_MACE_STRIKE,
}

internal data class MaceKillGroundPacketContext(
    val identityOwnedByRoute: Boolean,
    val kind: MaceKillMovementPacketKind,
)

/** Vanilla VClip uses its own ground bit and must never turn unrelated route packets into NoFall packets. */
internal fun shouldSpoofMaceKillVanillaVClipGround(packet: MaceKillGroundPacketContext): Boolean =
    packet.identityOwnedByRoute && packet.kind == MaceKillMovementPacketKind.VANILLA_VCLIP

internal fun shouldValidateMaceKillRouteSegment(
    clipAnchorOwned: Boolean,
    clipRecoveryOwned: Boolean,
    researchOwned: Boolean,
): Boolean = !clipAnchorOwned && !clipRecoveryOwned && !researchOwned
