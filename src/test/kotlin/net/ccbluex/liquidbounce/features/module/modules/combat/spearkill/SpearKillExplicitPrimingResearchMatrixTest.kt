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

package net.ccbluex.liquidbounce.features.module.modules.combat.spearkill

import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.collision.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.damage.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.direct.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.instant.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.profiled.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.schedule.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.attempt.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.movement.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*

import net.ccbluex.liquidbounce.features.module.modules.combat.*
import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
import net.ccbluex.liquidbounce.features.module.modules.combat.remotekill.*

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class SpearKillExplicitPrimingResearchMatrixTest {

    @TestFactory
    fun `explicit priming predicts the complete normal and Elytra research matrix`(): List<DynamicTest> =
        MOVEMENT_PROFILES.flatMap { profile ->
            PRIMING_PACKET_COUNTS.flatMap { primingPackets ->
                REQUESTED_DISTANCES.map { distance ->
                    DynamicTest.dynamicTest(
                        "${profile.name} N=$primingPackets distance=$distance",
                    ) {
                        val result = planExplicitBurst(
                            profile = profile,
                            distance = distance,
                            primingPackets = primingPackets,
                        )
                        val plan = assertInstanceOf(
                            SpearKillPrimedInstantPlanResult.Ready::class.java,
                            result,
                        ).plan
                        val finalPacketOrdinal = primingPackets + 1
                        val countedPackets = if (finalPacketOrdinal > 5) 1 else finalPacketOrdinal
                        val threshold = profile.squaredDistanceThreshold

                        assertEquals(primingPackets, plan.dedicatedPrimingPackets)
                        assertEquals(primingPackets, plan.totalPreFinalPackets)
                        assertEquals(finalPacketOrdinal, plan.finalPacketOrdinal)
                        assertEquals(countedPackets, plan.serverCountedPackets)
                        assertEquals(
                            distance * distance <= threshold * countedPackets,
                            plan.sourcePredictedAccepted,
                        )
                    }
                }
            }
        }
}












internal fun readyPlan(
    distance: Double,
    primingPackets: Int,
): SpearKillPrimedInstantPlan = assertInstanceOf(
    SpearKillPrimedInstantPlanResult.Ready::class.java,
    planExplicitBurst(SpearKillPrimedInstantMovementProfile.NORMAL, distance, primingPackets),
).plan

internal fun readyAutoPlan(
    distance: Double,
    expectedVelocitySquared: Double = 0.0,
    accounting: SpearKillPrimedInstantPacketAccounting = DEFAULT_ACCOUNTING,
): SpearKillPrimedInstantPlan = assertInstanceOf(
    SpearKillPrimedInstantPlanResult.Ready::class.java,
    SpearKillPrimedInstantPlanner.plan(
        request(
            distance = distance,
            expectedVelocitySquared = expectedVelocitySquared,
            priming = SpearKillPrimedInstantPriming.Auto,
            accounting = accounting,
        ),
    ),
).plan

internal fun planExplicitBurst(
    profile: SpearKillPrimedInstantMovementProfile,
    distance: Double,
    primingPackets: Int,
) = SpearKillPrimedInstantPlanner.plan(
    request(
        distance = distance,
        profile = profile,
        priming = SpearKillPrimedInstantPriming.Explicit(primingPackets),
    ),
)

internal fun request(
    distance: Double = 10.0,
    expectedVelocitySquared: Double = 0.0,
    profile: SpearKillPrimedInstantMovementProfile = SpearKillPrimedInstantMovementProfile.NORMAL,
    priming: SpearKillPrimedInstantPriming = SpearKillPrimedInstantPriming.Auto,
    accounting: SpearKillPrimedInstantPacketAccounting = DEFAULT_ACCOUNTING,
) = SpearKillPrimedInstantPlanRequest(
    requestedDistance = distance,
    expectedVelocitySquared = expectedVelocitySquared,
    movementProfile = profile,
    priming = priming,
    packetAccounting = accounting,
    primingPacketType = SpearKillPrimedInstantPacketType.Position,
)

internal fun assertBlocked(
    result: SpearKillPrimedInstantPlanResult,
    reason: SpearKillPrimedInstantBlockReason,
) {
    val blocked = assertInstanceOf(SpearKillPrimedInstantPlanResult.Blocked::class.java, result)
    assertEquals(reason, blocked.reason)
}

internal val MOVEMENT_PROFILES = SpearKillPrimedInstantMovementProfile.entries
internal val PRIMING_PACKET_COUNTS = listOf(0, 1, 2, 3, 4, 5, 9, 14, 18)
internal val REQUESTED_DISTANCES = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 75.0, 100.0, 125.0, 150.0, 200.0)
internal val DEFAULT_ACCOUNTING = SpearKillPrimedInstantPacketAccounting(
ownedPreFinalPackets = 0,
noFallPreFinalPackets = 0,
reservedPacketsAfterFinal = 0,
maxPackets = 512,
)
