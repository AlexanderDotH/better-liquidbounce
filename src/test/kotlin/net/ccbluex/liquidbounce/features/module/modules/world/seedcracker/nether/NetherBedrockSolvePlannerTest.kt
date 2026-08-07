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
package net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.nether

import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.ChunkCoordinate
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.CrackScope
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.EvidenceId
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockBitPlane
import net.ccbluex.liquidbounce.features.module.modules.world.seedcracker.NetherBedrockChunkObservation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class NetherBedrockSolvePlannerTest {

    @Test
    fun `five thousand observations retain two sources and one independent held-out chunk`() {
        val observations = (5_493 downTo 1).map(::observation)

        val plan = NetherBedrockSolvePlanner.plan(SCOPE, observations)

        assertEquals(listOf(1L, 2L), plan.sourceObservations.map { it.capturedOrder })
        assertEquals(3L, plan.heldOutObservation?.capturedOrder)
        assertEquals(3, plan.allObservations.size)
    }

    @Test
    fun `later chunks do not reset the selected evidence fingerprint`() {
        val selected = (1..3).map(::observation)
        val initial = NetherBedrockSolvePlanner.plan(SCOPE, selected)

        val withLaterChunk = NetherBedrockSolvePlanner.plan(SCOPE, selected + observation(4))
        val withSelectedRevision = NetherBedrockSolvePlanner.plan(
            SCOPE,
            selected.drop(1) + observation(1).copy(revision = 2L),
        )

        assertEquals(initial.fingerprint, withLaterChunk.fingerprint)
        assertNotEquals(initial.fingerprint, withSelectedRevision.fingerprint)
    }

    private fun observation(order: Int) = NetherBedrockChunkObservation(
        id = EvidenceId("nether:$order"),
        scope = SCOPE,
        chunk = ChunkCoordinate(order, -order),
        revision = 1L,
        floor = NetherBedrockBitPlane.empty(),
        roof = NetherBedrockBitPlane.empty(),
        capturedOrder = order.toLong(),
    )

    private companion object {
        val SCOPE = CrackScope("server", "minecraft:the_nether")
    }
}
