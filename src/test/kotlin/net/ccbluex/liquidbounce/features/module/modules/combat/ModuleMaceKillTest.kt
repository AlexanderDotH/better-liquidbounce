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
package net.ccbluex.liquidbounce.features.module.modules.combat

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

class ModuleMaceKillTest {

    companion object {
        init {
            MinecraftBootstrap.ensureInitialized()
        }
    }

    @Test
    fun `MaceKill preserves FallHeight first and exposes remote route settings`() {
        assertEquals(
            listOf(
                "Hidden",
                "FallHeight",
                "TargetDistance",
                "Activation",
                "TargetSource",
                "Movement",
                "Preview",
            ),
            ModuleMaceKill.inner.dropWhile { it.name != "Hidden" }.map { it.name },
        )

        val fallHeight = ModuleMaceKill.inner.single { it.name == "FallHeight" }
        assertEquals(22, fallHeight.get())
        @Suppress("UNCHECKED_CAST")
        val fallHeightRange = (fallHeight as RangedValue<Int>).range
        assertEquals(1..170, fallHeightRange)

        assertEquals(500f, ModuleMaceKill.inner.single { it.name == "TargetDistance" }.get())
        assertEquals(
            MaceKillActivationMode.HoldAttack,
            ModuleMaceKill.inner.single { it.name == "Activation" }.get(),
        )
        assertEquals(
            MaceKillTargetSource.Crosshair,
            ModuleMaceKill.inner.single { it.name == "TargetSource" }.get(),
        )

        @Suppress("UNCHECKED_CAST")
        val targetSource = ModuleMaceKill.inner.single { it.name == "TargetSource" }
            as ChoiceListValue<MaceKillTargetSource>
        assertEquals(listOf("Crosshair", "Combat"), targetSource.choices.map { it.tag })
        val preview = ModuleMaceKill.inner.single { it.name == "Preview" }
        assertTrue(preview is ToggleableValueGroup)
        assertTrue((preview as ToggleableValueGroup).enabled)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `Packet routing exposes only normal routes and keeps Direct as the safe default`() {
        val configuration = MaceKillMovementConfiguration(null)

        assertEquals("Packet", configuration.choice.activeMode.name)
        assertEquals(listOf("TargetSpeed", "Acceleration", "Deceleration"), configuration.choice.inner.map { it.name })
        assertEquals(
            mapOf(
                "Motion" to listOf("StepDistance"),
                "Packet" to listOf("StepDistance", "StepDelay", "Routing"),
            ),
            configuration.choice.modes.associate { it.name to it.inner.map { value -> value.name } },
        )

        val routing = configuration.packet.routing
        assertEquals("Direct", routing.activeMode.name)
        assertEquals(SPEAR_KILL_NORMAL_MAX_SPEED, configuration.targetSpeed)
        assertEquals(SPEAR_KILL_EXPERIMENTAL_MAX_SPEED, configuration.acceleration)
        assertEquals(SPEAR_KILL_ELYTRA_MAX_SPEED, configuration.packet.stepDistance)
        assertEquals(500, configuration.packet.aStar.maxCost)
        assertEquals(
            listOf("Direct", "AStar", "Instant"),
            routing.modes.map { it.name },
        )
        assertFalse(configuration.packet.direct.aliases.contains("ClipReach"))
        assertTrue(routing is ModeValueGroup<*>)
    }

    @Test
    fun `KillAura always uses packet travel and returns before another target`() {
        assertEquals(
            MaceKillRouteTransport.PACKET,
            selectMaceKillRouteTransport(
                configuredMotion = true,
                owner = MaceKillRouteOwner.KILL_AURA,
            ),
        )
        assertFalse(MaceKillRouteOwner.KILL_AURA.allowsTargetChain)
        assertTrue(MaceKillRouteOwner.MANUAL.allowsTargetChain)

        val request = RemoteKillRouteRequest(
            origin = Vec3(4.0, 70.0, -8.0),
            outboundMovements = listOf(
                Vec3(5.0, 0.0, 0.0),
                Vec3(0.0, 1.0, 3.0),
            ),
        )
        val finalOffset = request.roundTripMovements.fold(Vec3.ZERO, Vec3::add)

        assertEquals(Vec3.ZERO, finalOffset)
        assertFalse(request.physicalReturn)
    }

    @Test
    fun `shared route speed profile applies configured acceleration and deceleration`() {
        val accelerating = SpearKillSpeedProfile(
            currentSpeed = 2.0,
            limits = SpearKillSpeedLimits(10.0, 3.0, 1.0, 20.0, 20.0),
        )
        val decelerating = SpearKillSpeedProfile(
            currentSpeed = 12.0,
            limits = SpearKillSpeedLimits(10.0, 3.0, 1.0, 20.0, 20.0),
        )

        assertEquals(5.0, accelerating.stepAt(0).requestedSpeed)
        assertEquals(11.0, decelerating.stepAt(0).requestedSpeed)
    }

    @Test
    fun `exact inverse return finishes fall safety without a synthetic ground packet`() {
        val outbound = listOf(Vec3(0.0, 4.0, 0.0), Vec3(3.0, -4.0, 0.0))
        val plan = SpearKillServerFallSafetyPlan.create(
            outboundMovements = outbound,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            groundedSteps = listOf(false, false, false, true),
        ) as SpearKillServerFallSafetyPlanResult.Ready
        val lifecycle = SpearKillFallSafetyLifecycle().apply { begin(plan.plan) }
        val roundTrip = outbound + outbound.asReversed().map { it.scale(-1.0) }

        roundTrip.forEachIndexed { index, movement ->
            assertEquals(
                SpearKillFallSafetyPendingStepGate.CLEAR,
                lifecycle.gatePendingMovement(movement, physicallyNearGround = index == roundTrip.lastIndex),
            )
            assertTrue(
                lifecycle.confirmMovement(
                    movement,
                    delivered = true,
                    exactPacketGrounded = index == roundTrip.lastIndex,
                ),
            )
        }
        val finish = lifecycle.finish(
            finalPositionKnown = true,
            connectionOpen = true,
            physicallyNearGround = true,
        )

        assertTrue(finish.resetLocalFallDistance)
        assertFalse(finish.sendGroundedPacket)
    }

    @Test
    fun `HoldAttack launches once and retries only after resolution and cooldown`() {
        var state = MaceKillHoldAttackState.IDLE

        val first = advanceMaceKillHoldAttack(
            state = state,
            attackHeld = true,
            targetAvailable = true,
            routeActive = false,
            evidencePending = false,
            cooldownReady = true,
        )
        assertTrue(first.launch)
        state = first.state

        val active = advanceMaceKillHoldAttack(
            state = state,
            attackHeld = true,
            targetAvailable = true,
            routeActive = true,
            evidencePending = false,
            cooldownReady = true,
        )
        assertFalse(active.launch)

        val awaitingEvidence = advanceMaceKillHoldAttack(
            state = active.state,
            attackHeld = true,
            targetAvailable = true,
            routeActive = false,
            evidencePending = true,
            cooldownReady = true,
        )
        assertFalse(awaitingEvidence.launch)

        val coolingDown = advanceMaceKillHoldAttack(
            state = awaitingEvidence.state,
            attackHeld = true,
            targetAvailable = true,
            routeActive = false,
            evidencePending = false,
            cooldownReady = false,
        )
        assertFalse(coolingDown.launch)

        val retry = advanceMaceKillHoldAttack(
            state = coolingDown.state,
            attackHeld = true,
            targetAvailable = true,
            routeActive = false,
            evidencePending = false,
            cooldownReady = true,
        )
        assertTrue(retry.launch)
    }

    @Test
    fun `HoldAttack retries when evidence clears after cooldown is already ready`() {
        val decision = advanceMaceKillHoldAttack(
            state = MaceKillHoldAttackState.ATTEMPTED,
            attackHeld = true,
            targetAvailable = true,
            routeActive = false,
            evidencePending = false,
            cooldownReady = true,
        )

        assertTrue(decision.launch)
        assertEquals(MaceKillHoldAttackState.ATTEMPTED, decision.state)
    }

    @Test
    fun `releasing HoldAttack does not cancel a committed safe return`() {
        val result = advanceMaceKillHoldAttack(
            state = MaceKillHoldAttackState.ATTEMPTED,
            attackHeld = false,
            targetAvailable = false,
            routeActive = true,
            evidencePending = false,
            cooldownReady = true,
        )

        assertFalse(result.launch)
        assertTrue(result.keepRouteAlive)
        assertEquals(MaceKillHoldAttackState.IDLE, result.state)
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

    @Test
    fun `authoritative origin correction aborts an active route but confirms a completed return`() {
        assertEquals(
            MaceKillOriginCorrectionAction.ABORT_ACTIVE_ROUTE,
            maceKillOriginCorrectionAction(routeSessionActive = true),
        )
        assertEquals(
            MaceKillOriginCorrectionAction.CONFIRM_COMPLETED_RETURN,
            maceKillOriginCorrectionAction(routeSessionActive = false),
        )
    }

    @Test
    fun `packet route restores local drift to the captured start position`() {
        val origin = Vec3(4.0, 70.0, -8.0)

        assertEquals(
            origin,
            requiredMaceKillLocalRestore(
                packetRouteOwned = true,
                origin = origin,
                currentPosition = origin.add(0.25, 0.0, 0.0),
            ),
        )
        assertNull(requiredMaceKillLocalRestore(true, origin, origin))
        assertNull(requiredMaceKillLocalRestore(false, origin, origin.add(1.0, 0.0, 0.0)))
    }
}
