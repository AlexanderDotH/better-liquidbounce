/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.macekill

import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleMaceKill

import net.ccbluex.liquidbounce.features.module.modules.combat.fightbot.*
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
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.safety.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.preview.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.planner.astar.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.research.highspeed.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.*
import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.group.ModeValueGroup
import net.ccbluex.liquidbounce.config.types.group.ToggleableValueGroup
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue
import net.ccbluex.liquidbounce.test.MinecraftBootstrap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillTargetEndpointPoliciesFailClosedTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `target and endpoint policies fail closed`() {
        assertTrue(
            isMaceKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = false,
                isInWater = false,
            ),
        )
        assertFalse(
            isMaceKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = true,
                isInWater = false,
            ),
        )
        assertFalse(
            isMaceKillTargetCandidateEligible(
                isCombatSafe = true,
                isAlive = true,
                isInCurrentWorld = true,
                isWithinRange = true,
                isRejected = false,
                isInWater = true,
            ),
        )
        assertTrue(
            isMaceKillEndpointReady(
                holdingMace = true,
                bodySpaceClear = true,
                attackRayClear = true,
                cooldownReady = true,
                usableFallHeight = 22,
            ),
        )
        assertFalse(
            isMaceKillEndpointReady(
                holdingMace = true,
                bodySpaceClear = true,
                attackRayClear = false,
                cooldownReady = true,
                usableFallHeight = 22,
            ),
        )
        assertFalse(
            isMaceKillEndpointReady(
                holdingMace = true,
                bodySpaceClear = true,
                attackRayClear = true,
                cooldownReady = true,
                usableFallHeight = 0,
            ),
        )
    }

    @Test
    fun `ClipReach research look ray ignores terrain independently of the configured packet route`() {
        assertTrue(
            shouldMaceKillLookRayIgnoreTerrain(
                packetMovement = true,
                aStarRouting = false,
                instantRouting = false,
                clipReachResearch = true,
            ),
        )
        assertFalse(
            shouldMaceKillLookRayIgnoreTerrain(
                packetMovement = true,
                aStarRouting = false,
                instantRouting = false,
                clipReachResearch = false,
            ),
        )
        assertTrue(
            shouldMaceKillLookRayIgnoreTerrain(
                packetMovement = true,
                aStarRouting = true,
                instantRouting = false,
                clipReachResearch = false,
            ),
        )
        assertTrue(
            shouldMaceKillLookRayIgnoreTerrain(
                packetMovement = true,
                aStarRouting = false,
                instantRouting = true,
                clipReachResearch = false,
            ),
        )
    }

    @Test
    fun `packet route admission rejects movement contexts that cannot return safely`() {
        assertEquals(
            MaceKillRouteAdmissionFailure.PASSENGER,
            evaluateMaceKillRouteAdmission(MaceKillRouteAdmissionContext(passenger = true)),
        )
        assertEquals(
            MaceKillRouteAdmissionFailure.GLIDING,
            evaluateMaceKillRouteAdmission(MaceKillRouteAdmissionContext(gliding = true)),
        )
        assertEquals(
            MaceKillRouteAdmissionFailure.BLINK,
            evaluateMaceKillRouteAdmission(MaceKillRouteAdmissionContext(blinkRunning = true)),
        )
        assertEquals(
            MaceKillRouteAdmissionFailure.MOVEMENT_OWNED,
            evaluateMaceKillRouteAdmission(MaceKillRouteAdmissionContext(conflictingMovementOwned = true)),
        )
        assertNull(evaluateMaceKillRouteAdmission(MaceKillRouteAdmissionContext()))
    }

    @Test
    fun `hard route rejection backs KillAura off instead of planning every tick`() {
        val backoff = MaceKillRouteAdmissionBackoff(durationTicks = 20)

        backoff.reject(currentTick = 100)

        assertTrue(backoff.isBlocked(100))
        assertTrue(backoff.isBlocked(119))
        assertFalse(backoff.isBlocked(120))
        backoff.clear()
        assertFalse(backoff.isBlocked(100))
    }

    @Test
    fun `route timing uses the forced packet transport and frozen packet mode`() {
        val direct = MaceKillRouteTiming(
            transport = MaceKillRouteTransport.PACKET,
            stepDistance = 10.0,
            stepWaitTicks = 1,
        )
        val instant = MaceKillRouteTiming(
            transport = MaceKillRouteTransport.PACKET,
            stepDistance = 10.0,
            maxPacketsPerTick = 128,
        )
        val motion = MaceKillRouteTiming(
            transport = MaceKillRouteTransport.MOTION,
            stepDistance = 10.0,
        )

        assertEquals(5, direct.travelTicksForSteps(3))
        assertEquals(2, instant.travelTicksForSteps(129))
        assertEquals(3, motion.travelTicksForSteps(3))
        assertEquals(5, direct.predictedTravelTicks(25.0))
    }

    @Test
    fun `normal route deadline is bounded and target replans cannot extend it`() {
        assertEquals(140, maceKillRouteDeadlineTick(startTick = 100, oneWayTravelTicks = 5))
        assertEquals(340, maceKillRouteDeadlineTick(startTick = 100, oneWayTravelTicks = 10_000))
    }

    @Test
    fun `packet return waits for late corrections before releasing movement ownership`() {
        val confirmation = MaceKillReturnConfirmationWindow(graceTicks = 10)

        confirmation.onExactReturnDelivered(currentTick = 50)
        assertFalse(confirmation.shouldRelease(59))
        assertTrue(confirmation.shouldRelease(60))

        confirmation.onCorrection()
        assertFalse(confirmation.awaitingConfirmation)
        confirmation.onExactReturnDelivered(currentTick = 70)
        assertFalse(confirmation.shouldRelease(79))
        assertTrue(confirmation.shouldRelease(80))
    }

    @Test
    fun `disable releases only an already completed correction-window lease`() {
        assertEquals(
            MaceKillDisableRouteAction.RELEASE_COMPLETED,
            maceKillDisableRouteAction(sessionActive = false, awaitingStrike = false),
        )
        assertEquals(
            MaceKillDisableRouteAction.BEGIN_SAFE_ABORT,
            maceKillDisableRouteAction(sessionActive = true, awaitingStrike = false),
        )
        assertEquals(
            MaceKillDisableRouteAction.BEGIN_SAFE_ABORT,
            maceKillDisableRouteAction(sessionActive = false, awaitingStrike = true),
        )
    }

    @Test
    fun `Instant retains post-return ownership across Folia delayed corrections`() {
        assertEquals(160, maceKillReturnConfirmationTicks(MaceKillRoutingMode.INSTANT))
        assertEquals(10, maceKillReturnConfirmationTicks(MaceKillRoutingMode.DIRECT))
        assertEquals(10, maceKillReturnConfirmationTicks(MaceKillRoutingMode.A_STAR))

        val confirmation = MaceKillReturnConfirmationWindow(graceTicks = 10)
        confirmation.onExactReturnDelivered(currentTick = 50, confirmationTicks = 160)
        assertFalse(confirmation.shouldRelease(209))
        assertTrue(confirmation.shouldRelease(210))
    }

    @Test
    fun `correction recovery relocates the collision box to the authoritative route origin`() {
        val localPosition = Vec3(125.5, 101.0, 100.5)
        val authoritativePosition = Vec3(108.5, 198.0, 98.5)
        val localBox = AABB(125.2, 101.0, 100.2, 125.8, 102.8, 100.8)

        val relocated = maceKillBoundingBoxAtRouteOrigin(localBox, localPosition, authoritativePosition)

        assertEquals(108.2, relocated.minX, 1.0E-9)
        assertEquals(198.0, relocated.minY, 1.0E-9)
        assertEquals(98.2, relocated.minZ, 1.0E-9)
        assertEquals(108.8, relocated.maxX, 1.0E-9)
        assertEquals(199.8, relocated.maxY, 1.0E-9)
        assertEquals(98.8, relocated.maxZ, 1.0E-9)
    }

    @Test
    fun `remote strike waits until the confirmed endpoint packet is in an earlier server tick`() {
        val earliestStrikeTick = maceKillRemoteStrikeEarliestTick(
            confirmedEndpointTick = 100,
            instantClip = false,
        )

        assertTrue(shouldDeferMaceKillStrike(currentTick = 100, earliestStrikeTick))
        assertTrue(shouldDeferMaceKillStrike(currentTick = 101, earliestStrikeTick))
        assertFalse(shouldDeferMaceKillStrike(currentTick = 102, earliestStrikeTick))
        assertFalse(shouldDeferMaceKillStrike(currentTick = 100, earliestStrikeTick = 0))
        assertEquals(
            0,
            maceKillRemoteStrikeEarliestTick(confirmedEndpointTick = 100, instantClip = true),
        )
    }
}
