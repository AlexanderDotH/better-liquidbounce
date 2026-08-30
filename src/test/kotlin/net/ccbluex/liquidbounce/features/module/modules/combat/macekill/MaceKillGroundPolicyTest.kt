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

package net.ccbluex.liquidbounce.features.module.modules.combat.macekill



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillGroundPolicyTest {

    @Test
    fun `clip spoof applies only to an identity-owned ClipReach anchor packet`() {
        val ownedClipAnchor = MaceKillGroundPacketContext(
            identityOwnedByRoute = true,
            kind = MaceKillMovementPacketKind.CLIP_REACH_ANCHOR,
        )

        assertTrue(MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF.shouldSpoofOnGround(ownedClipAnchor))
        assertFalse(MaceKillGroundPolicy.COLLISION_DERIVED.shouldSpoofOnGround(ownedClipAnchor))
    }

    @Test
    fun `Instant correction recovery retains the identity-scoped no-fall spoof`() {
        val ownedRecovery = MaceKillGroundPacketContext(
            identityOwnedByRoute = true,
            kind = MaceKillMovementPacketKind.CLIP_REACH_RECOVERY,
        )

        assertTrue(MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF.shouldSpoofOnGround(ownedRecovery))
        assertFalse(MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF.shouldSpoofOnGround(ownedRecovery.copy(
            identityOwnedByRoute = false,
        )))
    }

    @Test
    fun `clip spoof cannot leak to Direct AStar foreign or mace strike packets`() {
        val excludedPackets = listOf(
            MaceKillGroundPacketContext(true, MaceKillMovementPacketKind.DIRECT_ROUTE),
            MaceKillGroundPacketContext(true, MaceKillMovementPacketKind.ASTAR_ROUTE),
            MaceKillGroundPacketContext(false, MaceKillMovementPacketKind.CLIP_REACH_ANCHOR),
            MaceKillGroundPacketContext(true, MaceKillMovementPacketKind.INSTANT_MACE_STRIKE),
        )

        excludedPackets.forEach { packet ->
            assertFalse(MaceKillGroundPolicy.CLIP_ANCHOR_SPOOF.shouldSpoofOnGround(packet), packet.toString())
        }
    }

    @Test
    fun `Vanilla VClip ground flag is confined to its route owned packet`() {
        val ownedVClip = MaceKillGroundPacketContext(
            identityOwnedByRoute = true,
            kind = MaceKillMovementPacketKind.VANILLA_VCLIP,
        )

        assertTrue(shouldSpoofMaceKillVanillaVClipGround(ownedVClip))
        assertFalse(shouldSpoofMaceKillVanillaVClipGround(ownedVClip.copy(
            identityOwnedByRoute = false,
        )))
        assertFalse(shouldSpoofMaceKillVanillaVClipGround(ownedVClip.copy(
            kind = MaceKillMovementPacketKind.DIRECT_ROUTE,
        )))
    }

    @Test
    fun `full inverse ClipReach recovery keeps the same intermediate collision exception`() {
        assertFalse(shouldValidateMaceKillRouteSegment(
            clipAnchorOwned = false,
            clipRecoveryOwned = true,
            researchOwned = false,
        ))
        assertTrue(shouldValidateMaceKillRouteSegment(
            clipAnchorOwned = false,
            clipRecoveryOwned = false,
            researchOwned = false,
        ))
    }
}
