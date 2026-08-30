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
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.runtime.*
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

class MaceKillPreservesFallHeightFirstExposesRemoteRouteTest {

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
        val previewMode = preview.inner.single { it.name == "Mode" } as ModeValueGroup<*>
        assertEquals("Glow", previewMode.activeMode.name)
        assertEquals(listOf("Glow"), previewMode.modes.map { it.name })
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
        assertEquals(MACE_KILL_NORMAL_MAX_SPEED, configuration.targetSpeed)
        assertEquals(MACE_KILL_EXPERIMENTAL_MAX_SPEED, configuration.acceleration)
        assertEquals(MACE_KILL_ELYTRA_MAX_SPEED, configuration.packet.stepDistance)
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
        val accelerating = MaceKillSpeedProfile(
            currentSpeed = 2.0,
            limits = MaceKillSpeedLimits(10.0, 3.0, 1.0, 20.0, 20.0),
        )
        val decelerating = MaceKillSpeedProfile(
            currentSpeed = 12.0,
            limits = MaceKillSpeedLimits(10.0, 3.0, 1.0, 20.0, 20.0),
        )

        assertEquals(5.0, accelerating.stepAt(0).requestedSpeed)
        assertEquals(11.0, decelerating.stepAt(0).requestedSpeed)
    }

    @Test
    fun `exact inverse return finishes fall safety without a synthetic ground packet`() {
        val outbound = listOf(Vec3(0.0, 4.0, 0.0), Vec3(3.0, -4.0, 0.0))
        val plan = MaceKillServerFallSafetyPlan.create(
            outboundMovements = outbound,
            initialFallDistance = 0.0,
            safeFallDistance = 3.0,
            groundedSteps = listOf(false, false, false, true),
        ) as MaceKillServerFallSafetyPlanResult.Ready
        val lifecycle = MaceKillFallSafetyLifecycle().apply { begin(plan.plan) }
        val roundTrip = outbound + outbound.asReversed().map { it.scale(-1.0) }

        roundTrip.forEachIndexed { index, movement ->
            assertEquals(
                MaceKillFallSafetyPendingStepGate.CLEAR,
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
}
