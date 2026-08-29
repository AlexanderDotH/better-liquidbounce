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
package net.ccbluex.liquidbounce.features.litematica.application

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActionKind
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaActionPriority
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBlockSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaBounds
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaCellSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementMethod
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlannerSettings
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPoint
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCapabilities
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCellInteractionSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceExecutionToken
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceRequest
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaIntegrationVersions
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaMaterialSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaOperationResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementPositionProvider
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPositionProviderLease
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanBatch
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanRequest
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaVerifierSnapshot
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LitematicaScanCoordinatorTest {

    @Test
    fun `completed scan generation replaces stale interaction metadata`() {
        val first = position(0)
        val second = position(1)
        val port = FakePort(ArrayDeque(listOf(batch(first), batch(second))))
        val coordinator = LitematicaScanCoordinator(port)

        coordinator.scan(ORIGIN, LitematicaPlannerSettings(), emptySet())
        assertNotNull(coordinator.interactionFor(action(first)))

        coordinator.scan(ORIGIN, LitematicaPlannerSettings(), emptySet())
        assertNull(coordinator.interactionFor(action(first)))
        assertNotNull(coordinator.interactionFor(action(second)))
    }

    @Test
    fun `adapter restart discards partial cells and requests runtime cleanup`() {
        val stale = position(0)
        val current = position(1)
        val partial = batch(stale).copy(
            nextCursor = net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanCursor("old"),
            complete = false,
        )
        val restarted = batch(current).copy(restartGeneration = true)
        val coordinator = LitematicaScanCoordinator(FakePort(ArrayDeque(listOf(partial, restarted))))

        assertNull(coordinator.scan(ORIGIN, settings(), emptySet()))
        val update = coordinator.scan(ORIGIN, settings(), emptySet())

        assertTrue(update?.placementChanged == true)
        assertNull(coordinator.interactionFor(action(stale)))
        assertNotNull(coordinator.interactionFor(action(current)))
    }

    private fun batch(position: LitematicaPosition): LitematicaScanBatch {
        val placement = placement(position)
        return LitematicaScanBatch(
            placements = listOf(placement),
            interactions = listOf(
                LitematicaCellInteractionSnapshot(
                    placementId = ID,
                    position = position,
                    neighborHitResult = null,
                    rotationTarget = Vec3(position.x + 0.5, position.y + 0.5, position.z + 0.5),
                ),
            ),
            nextCursor = null,
            complete = true,
        )
    }

    private fun placement(position: LitematicaPosition) = LitematicaPlacementSnapshot(
        id = ID,
        name = "Test",
        enabled = true,
        rendered = true,
        bounds = LitematicaBounds(position, position),
        cells = listOf(
            LitematicaCellSnapshot(
                position = position,
                desired = STONE,
                actual = AIR,
                placementMethod = LitematicaPlacementMethod.AIR_PLACE,
                requiredMaterialId = "minecraft:stone",
            ),
        ),
    )

    private fun action(position: LitematicaPosition) = LitematicaPrintAction(
        target = position,
        placementId = ID,
        kind = LitematicaActionKind.AIR_PLACE,
        priority = LitematicaActionPriority.AIRPLACE_SOLID,
        desired = STONE,
        actual = AIR,
        distanceSquared = 0.0,
    )

    private fun position(x: Int) = LitematicaPosition(x, 64, 0)

    private fun settings() = LitematicaPlannerSettings()

    private class FakePort(
        private val batches: ArrayDeque<LitematicaScanBatch>,
    ) : LitematicaPort {
        override val versions = LitematicaIntegrationVersions("0.28.4", "0.29.3")
        override val capabilities = LitematicaCapabilities.required()

        override fun placementMetadata() = listOf(
            LitematicaPlacementMetadataSnapshot(ID, "Test", true, true, BOUNDS,
                net.ccbluex.liquidbounce.features.litematica.domain.LitematicaRenderLayer.ALL),
        )
        override fun scanPlacements(request: LitematicaScanRequest) = batches.removeFirst()
        override fun materials(placementId: LitematicaPlacementId): List<LitematicaMaterialSnapshot> = emptyList()
        override fun easyPlaceSnapshot() = LitematicaEasyPlaceSnapshot.disabled()
        override fun setEasyPlaceEnabled(enabled: Boolean) = LitematicaOperationResult.Accepted
        override fun verifier(placementId: LitematicaPlacementId): LitematicaVerifierSnapshot? = null
        override fun executeEasyPlace(
            request: LitematicaEasyPlaceRequest,
            token: LitematicaEasyPlaceExecutionToken,
        ) = LitematicaEasyPlaceResult.Submitted
        override fun installPositionProvider(provider: LitematicaPlacementPositionProvider) =
            LitematicaPositionProviderLease.NONE
        override fun close() = Unit
    }

    private companion object {
        val ID = LitematicaPlacementId("test")
        val ORIGIN = LitematicaPoint(0.5, 64.5, 0.5)
        val BOUNDS = LitematicaBounds(LitematicaPosition(0, 64, 0), LitematicaPosition(1, 64, 0))
        val AIR = LitematicaBlockSnapshot.air()
        val STONE = LitematicaBlockSnapshot.solid("minecraft:stone")
    }
}
