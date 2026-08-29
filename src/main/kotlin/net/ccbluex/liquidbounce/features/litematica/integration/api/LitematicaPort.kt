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
package net.ccbluex.liquidbounce.features.litematica.integration.api

import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId

interface LitematicaPort : AutoCloseable {
    val versions: LitematicaIntegrationVersions
    val capabilities: LitematicaCapabilities

    fun placementMetadata(): List<LitematicaPlacementMetadataSnapshot>
    fun scanPlacements(request: LitematicaScanRequest): LitematicaScanBatch
    fun materials(placementId: LitematicaPlacementId): List<LitematicaMaterialSnapshot>
    fun easyPlaceSnapshot(): LitematicaEasyPlaceSnapshot
    fun setEasyPlaceEnabled(enabled: Boolean): LitematicaOperationResult
    fun verifier(placementId: LitematicaPlacementId): LitematicaVerifierSnapshot?

    fun executeEasyPlace(
        request: LitematicaEasyPlaceRequest,
        token: LitematicaEasyPlaceExecutionToken,
    ): LitematicaEasyPlaceResult

    /** Replaces this port's previous provider. Closing the lease is idempotent. */
    fun installPositionProvider(
        provider: LitematicaPlacementPositionProvider,
    ): LitematicaPositionProviderLease

    /** Removes owned provider state and leaves Litematica configuration unchanged. */
    override fun close()
}

sealed interface LitematicaOperationResult {
    data object Accepted : LitematicaOperationResult
    data class Rejected(val detail: String) : LitematicaOperationResult
}
