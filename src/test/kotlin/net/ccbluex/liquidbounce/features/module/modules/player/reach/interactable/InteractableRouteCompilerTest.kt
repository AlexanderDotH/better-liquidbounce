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
package net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable

import net.ccbluex.liquidbounce.features.module.modules.movement.vclip.VClipFallSafetyContext
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteKind
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePathKind
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRoutePlan
import net.ccbluex.liquidbounce.features.module.modules.player.reach.interactable.route.InteractableRouteSegment
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InteractableRouteCompilerTest {

    @Test
    fun `same-stance extended interaction still confirms one anchored packet`() {
        val origin = Vec3(0.25, 64.0, -0.75)
        val plan = InteractableRoutePlan(
            InteractableRouteKind.DIRECT,
            listOf(InteractableRouteSegment.Path(InteractableRoutePathKind.DIRECT, listOf(origin))),
        )

        val route = (InteractableRouteCompiler.compile(
            plan,
            stepDistance = 9.5,
            maximumVClipDistance = 30.0,
            vClip = InteractableVClipSettings.Vanilla(false, false),
            fallSafety = VClipFallSafetyContext(0.0, 3.0),
        ) as InteractableRouteCompileResult.Ready).route

        assertEquals(1, route.steps.size)
        assertEquals(origin, route.endpoint)
        assertTrue(route.steps.single().inverse.isEmpty())
    }

    @Test
    fun `long direct shortcut is packetized below the captured step distance with exact inverses`() {
        val origin = Vec3(0.25, 64.0, 0.75)
        val endpoint = Vec3(20.25, 64.0, 0.75)
        val plan = InteractableRoutePlan(
            InteractableRouteKind.DIRECT,
            listOf(InteractableRouteSegment.Path(InteractableRoutePathKind.DIRECT, listOf(origin, endpoint))),
        )

        val result = InteractableRouteCompiler.compile(
            plan = plan,
            stepDistance = 9.5,
            maximumVClipDistance = 30.0,
            vClip = InteractableVClipSettings.Vanilla(false, false),
            fallSafety = VClipFallSafetyContext(0.0, 3.0),
        )

        val route = (result as InteractableRouteCompileResult.Ready).route
        assertEquals(3, route.steps.size)
        assertEquals(endpoint, route.endpoint)
        assertTrue(route.steps.zip(listOf(origin) + route.steps.map { it.outbound.confirmedPosition })
            .all { (step, previous) -> step.outbound.confirmedPosition.distanceTo(previous) <= 9.5 + 1.0E-9 })
        assertEquals(origin, route.exactReturnForPrefix(route.steps.size).last().confirmedPosition)
    }

    @Test
    fun `surface route compiles Folia descent primers and returns through the exact composite inverse`() {
        val origin = Vec3(0.5, 10.0, 0.5)
        val surface = Vec3(2.5, 11.0, 0.5)
        val anchor = Vec3(5.5, 11.0, 0.5)
        val target = Vec3(5.5, 5.0, 0.5)
        val plan = InteractableRoutePlan(
            InteractableRouteKind.SURFACE,
            listOf(
                InteractableRouteSegment.Path(
                    InteractableRoutePathKind.CAVE_EGRESS,
                    listOf(origin, surface),
                ),
                InteractableRouteSegment.Path(
                    InteractableRoutePathKind.SURFACE_TRAVERSE,
                    listOf(surface, anchor),
                ),
                InteractableRouteSegment.VerticalClip(anchor, target),
            ),
        )

        val result = InteractableRouteCompiler.compile(
            plan = plan,
            stepDistance = 9.5,
            maximumVClipDistance = 30.0,
            vClip = InteractableVClipSettings.Folia(movementPackets = 5, fullPacket = false),
            fallSafety = VClipFallSafetyContext(0.0, 3.0),
        )

        val route = (result as InteractableRouteCompileResult.Ready).route
        assertEquals(target, route.endpoint)
        val vClipPackets = route.steps.filter { it.outbound.payload.transportBurstId != null }
        assertTrue(vClipPackets.any { it.outbound.payload is InteractablePacketInstruction.Status })
        assertEquals(1, vClipPackets.map { it.outbound.payload.transportBurstId }.distinct().size)
        val targetPackets = route.steps.map { it.outbound }.filter { it.confirmedPosition == target }
        assertTrue(targetPackets.isNotEmpty())
        assertTrue(targetPackets.all {
            (it.payload as InteractablePacketInstruction.Position).requiresStandableEndpoint
        })
        val exactReturn = route.exactReturnForPrefix(route.steps.size)
        assertEquals(origin, exactReturn.last().confirmedPosition)
        assertEquals(
            1,
            exactReturn.mapNotNull { it.payload.transportBurstId }.distinct().size,
        )
        assertTrue(exactReturn.filter { it.confirmedPosition == anchor }.all {
            (it.payload as InteractablePacketInstruction.Position).requiresStandableEndpoint
        })
        assertInstanceOf(
            InteractablePacketInstruction.Position::class.java,
            exactReturn.last().payload,
        )
    }

    @Test
    fun `unavailable VClip profile fails without returning a partial session route`() {
        val origin = Vec3(0.5, 10.0, 0.5)
        val surface = Vec3(0.5, 11.0, 0.5)
        val target = Vec3(0.5, 5.0, 0.5)
        val plan = InteractableRoutePlan(
            InteractableRouteKind.SURFACE,
            listOf(
                InteractableRouteSegment.Path(
                    InteractableRoutePathKind.CAVE_EGRESS,
                    listOf(origin, surface),
                ),
                InteractableRouteSegment.Path(
                    InteractableRoutePathKind.SURFACE_TRAVERSE,
                    listOf(surface),
                ),
                InteractableRouteSegment.VerticalClip(surface, target),
            ),
        )

        val result = InteractableRouteCompiler.compile(
            plan = plan,
            stepDistance = 9.5,
            maximumVClipDistance = 30.0,
            vClip = InteractableVClipSettings.Folia(movementPackets = 1, fullPacket = false),
            fallSafety = VClipFallSafetyContext(0.0, 0.0),
        )

        assertEquals(InteractableRouteCompileResult.VClipUnavailable, result)
    }

    @Test
    fun `compiler refuses a vertical clip beyond the captured reliability limit`() {
        val origin = Vec3(0.5, 70.0, 0.5)
        val target = Vec3(0.5, 0.0, 0.5)
        val plan = InteractableRoutePlan(
            InteractableRouteKind.CAVE_CLIP,
            listOf(InteractableRouteSegment.VerticalClip(origin, target)),
        )

        val result = InteractableRouteCompiler.compile(
            plan = plan,
            stepDistance = 9.5,
            maximumVClipDistance = 30.0,
            vClip = InteractableVClipSettings.Vanilla(false, false),
            fallSafety = VClipFallSafetyContext(0.0, 3.0),
        )

        assertEquals(InteractableRouteCompileResult.VClipDistanceExceeded, result)
    }

    @Test
    fun `compiler accepts a vertical clip exactly at the captured reliability limit`() {
        val origin = Vec3(0.5, 60.0, 0.5)
        val target = Vec3(0.5, 30.0, 0.5)
        val plan = InteractableRoutePlan(
            InteractableRouteKind.CAVE_CLIP,
            listOf(InteractableRouteSegment.VerticalClip(origin, target)),
        )

        val result = InteractableRouteCompiler.compile(
            plan = plan,
            stepDistance = 9.5,
            maximumVClipDistance = 30.0,
            vClip = InteractableVClipSettings.Vanilla(false, false),
            fallSafety = VClipFallSafetyContext(0.0, 3.0),
        )

        assertInstanceOf(InteractableRouteCompileResult.Ready::class.java, result)
    }
}
