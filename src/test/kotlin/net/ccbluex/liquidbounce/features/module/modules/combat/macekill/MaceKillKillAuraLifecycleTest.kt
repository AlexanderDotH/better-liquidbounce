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



import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.config.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.debug.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.correction.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.planner.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.lifecycle.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.research.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.target.*
import net.ccbluex.liquidbounce.features.module.modules.combat.macekill.contract.*
import net.ccbluex.liquidbounce.features.module.modules.combat.spearkill.session.packet.SpearKillPacketSessionPortAdapter
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

import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraAttackRoute
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.KillAuraRemoteWeapon
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.resolveKillAuraMaceLaunch
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.selectKillAuraRemoteKillRoute
import net.ccbluex.liquidbounce.test.assertVec3Equals
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaceKillKillAuraLifecycleTest {

    @Test
    fun `KillAura disable before the endpoint returns only confirmed movement without striking`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        var strikes = 0
        val engine = RemoteKillRouteEngine(
            session = session,
            weaponAdapter = RemoteKillWeaponAdapter<String> {
                strikes++
                RemoteKillStrikeResult.Committed
            },
            movementOwner = "mace-killaura-disable-test",
        )
        val origin = Vec3(10.0, 64.0, -4.0)
        engine.start(
            "target",
            RemoteKillRouteRequest(origin, listOf(Vec3(1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))),
        )

        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        engine.abort()

        assertEquals(0, strikes)
        assertTrue(session.recovering)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)

        assertEquals(0, strikes)
        assertFalse(session.active)
        assertFalse(engine.ownsMovement)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1.0E-12)
    }

    @Test
    fun `correction before strike replaces the route with an exact non attacking recovery`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        var strikes = 0
        val engine = RemoteKillRouteEngine(
            session = session,
            weaponAdapter = RemoteKillWeaponAdapter<String> {
                strikes++
                RemoteKillStrikeResult.Committed
            },
            movementOwner = "mace-killaura-correction-test",
        )
        val origin = Vec3(10.0, 64.0, -4.0)
        engine.start(
            "target",
            RemoteKillRouteRequest(origin, listOf(Vec3(1.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0))),
        )
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)

        engine.beginPacketExactRecoveryFrom(
            authoritativeOffset = Vec3(1.25, 0.0, 0.0),
            recoveryMovements = listOf(Vec3(-1.25, 0.0, 0.0)),
        )
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)

        assertEquals(0, strikes)
        assertFalse(session.active)
        assertFalse(engine.ownsMovement)
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1.0E-12)
    }

    @Test
    fun `KillAura transfers one strike and retains exact return ownership through a late correction`() {
        val session = SpearKillPacketBootSession(SpearKillPacketSessionPortAdapter())
        var strikes = 0
        val engine = RemoteKillRouteEngine(
            session = session,
            weaponAdapter = RemoteKillWeaponAdapter<String> {
                strikes++
                RemoteKillStrikeResult.Committed
            },
            movementOwner = "mace-killaura-integration-test",
            retainMovementAfterCompletion = true,
        )
        val origin = Vec3(10.0, 64.0, -4.0)
        val request = RemoteKillRouteRequest(origin, listOf(Vec3(2.0, 0.0, 0.0)))
        val selected = selectKillAuraRemoteKillRoute(
            delegateKillAuraAttacks = true,
            normalAttackPossible = true,
            heldRemoteWeapon = KillAuraRemoteWeapon.MACE,
            maceKillAvailable = true,
            maceKillTargetPossible = true,
            spearKillAvailable = true,
            spearKillTargetPossible = true,
            reachHitAvailable = true,
            reachHitTargetPossible = true,
        )

        val resolved = resolveKillAuraMaceLaunch(
            selectedRoute = selected,
            launchMaceKill = {
                engine.start("target", request)
                true
            },
            fallbackRoute = { error("an accepted route must not fall back") },
        )

        assertEquals(KillAuraAttackRoute.MACE_KILL, resolved)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        assertEquals(1, strikes)
        assertFalse(session.active)
        assertTrue(engine.ownsMovement)

        val confirmation = MaceKillReturnConfirmationWindow(graceTicks = 10)
        confirmation.onExactReturnDelivered(currentTick = 20)
        confirmation.onCorrection()
        engine.beginPacketExactRecoveryFrom(
            authoritativeOffset = Vec3(0.25, 0.0, 0.0),
            recoveryMovements = listOf(Vec3(-0.25, 0.0, 0.0)),
        )
        engine.prepareNextStep()
        engine.confirmStep(delivered = true)
        confirmation.onExactReturnDelivered(currentTick = 21)

        assertEquals(1, strikes)
        assertFalse(confirmation.shouldRelease(30))
        assertTrue(confirmation.shouldRelease(31))
        assertVec3Equals(Vec3.ZERO, session.committedOffset, 1.0E-12)
        engine.releaseCompletedOwnership()
        assertFalse(engine.ownsMovement)
    }
}
