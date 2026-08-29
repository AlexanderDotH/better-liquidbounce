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
package net.ccbluex.liquidbounce.features.litematica.integration.loader

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaBridgeResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCapabilities
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCapability
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceExecutionToken
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceRequest
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaIntegrationVersions
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaMaterialSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaOperationResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementPositionProvider
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPlacementMetadataSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPortFactory
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPositionProviderLease
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanBatch
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaScanRequest
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaUnavailableReason
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaVerifierSnapshot
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReflectiveLitematicaPortLoaderTest {

    @AfterEach
    fun clearFactoryResult() {
        TestBridgeFactory.result = null
    }

    @Test
    fun `missing mod fails before resolving integration classes`() {
        val requestedClasses = mutableListOf<String>()
        val loader = loader(
            versions = mapOf(LITEMATICA_ID to LITEMATICA_VERSION),
            requestedClasses = requestedClasses,
        )

        val result = assertIs<LitematicaPortLoadResult.Unavailable>(loader.load())

        assertEquals(LitematicaUnavailableReason.MISSING_MOD, result.availability.reason)
        assertTrue(result.availability.detail.contains(MALILIB_ID))
        assertTrue(requestedClasses.isEmpty())
    }

    @Test
    fun `incompatible MaLiLib version fails closed before class loading`() {
        val requestedClasses = mutableListOf<String>()
        val loader = loader(
            versions = exactVersions() + (MALILIB_ID to "0.29.4"),
            requestedClasses = requestedClasses,
        )

        val result = assertIs<LitematicaPortLoadResult.Unavailable>(loader.load())

        assertEquals(LitematicaUnavailableReason.VERSION_MISMATCH, result.availability.reason)
        assertTrue(result.availability.detail.contains("0.29.3"))
        assertTrue(requestedClasses.isEmpty())
    }

    @Test
    fun `missing external API class fails without initializing bridge`() {
        val loader = loader(
            versions = exactVersions(),
            missingClass = REQUIRED_CLASS,
        )

        val result = assertIs<LitematicaPortLoadResult.Unavailable>(loader.load())

        assertEquals(LitematicaUnavailableReason.MISSING_CLASS, result.availability.reason)
        assertTrue(result.availability.detail.contains(REQUIRED_CLASS))
    }

    @Test
    fun `bridge without every required capability is rejected`() {
        TestBridgeFactory.result = LitematicaBridgeResult.Unsupported(
            capabilities = LitematicaCapabilities.of(LitematicaCapability.PLACEMENT_METADATA),
            detail = "easy place entry point missing",
        )
        val loader = loader(versions = exactVersions())

        val result = assertIs<LitematicaPortLoadResult.Unavailable>(loader.load())

        assertEquals(LitematicaUnavailableReason.CAPABILITY_MISMATCH, result.availability.reason)
        assertTrue(result.availability.detail.contains("easy place entry point missing"))
        assertEquals(
            setOf(LitematicaCapability.PLACEMENT_METADATA),
            result.availability.capabilities?.supported,
        )
    }

    @Test
    fun `version lookup failure remains an unavailable optional integration`() {
        val loader = ReflectiveLitematicaPortLoader(
            versionLookup = LitematicaModVersionLookup { error("loader unavailable") },
        )

        val result = assertIs<LitematicaPortLoadResult.Unavailable>(loader.load())

        assertEquals(LitematicaUnavailableReason.BRIDGE_FAILURE, result.availability.reason)
        assertTrue(result.availability.detail.contains("loader unavailable"))
    }

    @Test
    fun `exact versions and complete bridge return ready port`() {
        val port = TestPort()
        TestBridgeFactory.result = LitematicaBridgeResult.Ready(port)
        val loader = loader(versions = exactVersions())

        val result = assertIs<LitematicaPortLoadResult.Ready>(loader.load())

        assertSame(port, result.port)
        assertEquals(LITEMATICA_VERSION, result.availability.versions.litematica)
        assertEquals(MALILIB_VERSION, result.availability.versions.malilib)
    }

    private fun loader(
        versions: Map<String, String>,
        requestedClasses: MutableList<String> = mutableListOf(),
        missingClass: String? = null,
    ) = ReflectiveLitematicaPortLoader(
        versionLookup = LitematicaModVersionLookup(versions::get),
        classLookup = LitematicaClassLookup { name, _ ->
            requestedClasses += name
            if (name == missingClass) throw ClassNotFoundException(name)
            if (name == BRIDGE_CLASS) TestBridgeFactory::class.java else String::class.java
        },
        requiredExternalClasses = listOf(REQUIRED_CLASS),
        bridgeFactoryClassName = BRIDGE_CLASS,
    )

    private fun exactVersions() = mapOf(
        LITEMATICA_ID to LITEMATICA_VERSION,
        MALILIB_ID to MALILIB_VERSION,
    )

    class TestBridgeFactory : LitematicaPortFactory {
        override fun create(): LitematicaBridgeResult = checkNotNull(result)

        companion object {
            var result: LitematicaBridgeResult? = null
        }
    }

    private class TestPort : LitematicaPort {
        override val versions = LitematicaIntegrationVersions(LITEMATICA_VERSION, MALILIB_VERSION)
        override val capabilities = LitematicaCapabilities.required()

        override fun placementMetadata(): List<LitematicaPlacementMetadataSnapshot> = emptyList()
        override fun scanPlacements(request: LitematicaScanRequest) = LitematicaScanBatch(
            placements = emptyList(),
            interactions = emptyList(),
            nextCursor = null,
            complete = true,
        )
        override fun materials(placementId: LitematicaPlacementId): List<LitematicaMaterialSnapshot> = emptyList()
        override fun easyPlaceSnapshot(): LitematicaEasyPlaceSnapshot = LitematicaEasyPlaceSnapshot.disabled()
        override fun setEasyPlaceEnabled(enabled: Boolean) = LitematicaOperationResult.Accepted
        override fun verifier(placementId: LitematicaPlacementId): LitematicaVerifierSnapshot? = null
        override fun executeEasyPlace(
            request: LitematicaEasyPlaceRequest,
            token: LitematicaEasyPlaceExecutionToken,
        ): LitematicaEasyPlaceResult = LitematicaEasyPlaceResult.Submitted

        override fun installPositionProvider(
            provider: LitematicaPlacementPositionProvider,
        ): LitematicaPositionProviderLease = LitematicaPositionProviderLease.NONE

        override fun close() = Unit
    }

    private companion object {
        const val LITEMATICA_ID = "litematica"
        const val MALILIB_ID = "malilib"
        const val LITEMATICA_VERSION = "0.28.4"
        const val MALILIB_VERSION = "0.29.3"
        const val REQUIRED_CLASS = "external.RequiredApi"
        const val BRIDGE_CLASS = "test.BridgeFactory"
    }
}
