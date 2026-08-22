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

package net.ccbluex.liquidbounce.features.module.modules.combat

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
