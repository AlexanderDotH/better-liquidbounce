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
package net.ccbluex.liquidbounce.features.litematica.integration.litematica262

import fi.dy.masa.litematica.config.Configs
import fi.dy.masa.litematica.config.Hotkeys
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.util.EasyPlaceUtils
import fi.dy.masa.litematica.world.SchematicWorldHandler
import fi.dy.masa.malilib.interoperation.IBlockPlacementPositionProvider
import fi.dy.masa.malilib.registry.Registry
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPlacementId
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCapabilities
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceExecutionToken
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceRequest
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceStrategy
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaHotkeySnapshot
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
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries

internal class Litematica262Port(
    override val capabilities: LitematicaCapabilities,
) : LitematicaPort {
    override val versions = LitematicaIntegrationVersions("0.28.4", "0.29.3")

    private val scanner = Litematica262PlacementScanner()
    private var providerLease: ProviderLease? = null

    override fun placementMetadata(): List<LitematicaPlacementMetadataSnapshot> = scanner.metadata()

    override fun scanPlacements(request: LitematicaScanRequest): LitematicaScanBatch = scanner.scan(request)

    override fun materials(placementId: LitematicaPlacementId): List<LitematicaMaterialSnapshot> {
        val placement = scanner.placement(placementId) ?: return emptyList()
        val materialList = placement.materialList ?: return emptyList()
        return materialList.materialsAll.map { entry ->
            val stack = entry.stack
            LitematicaMaterialSnapshot(
                materialId = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                requiredCount = entry.countTotal,
                availableCount = Litematica262Inventory.availableCount(stack),
            )
        }
    }

    override fun easyPlaceSnapshot() = LitematicaEasyPlaceSnapshot(
        enabled = Configs.Generic.EASY_PLACE_MODE.booleanValue,
        hotkey = LitematicaHotkeySnapshot(
            easyPlaceHeld = Hotkeys.EASY_PLACE_ACTIVATION.keybind.isKeybindHeld,
        ),
    )

    override fun setEasyPlaceEnabled(enabled: Boolean): LitematicaOperationResult = runCatching {
        Configs.Generic.EASY_PLACE_MODE.booleanValue = enabled
    }.fold(
        onSuccess = { LitematicaOperationResult.Accepted },
        onFailure = { LitematicaOperationResult.Rejected(it.message ?: it.javaClass.simpleName) },
    )

    override fun verifier(placementId: LitematicaPlacementId): LitematicaVerifierSnapshot? {
        val placement = scanner.placement(placementId) ?: return null
        if (!placement.hasVerifier()) return null
        val verifier = placement.schematicVerifier ?: return null
        return LitematicaVerifierSnapshot(
            correct = verifier.correctStatesCount,
            missing = verifier.missingBlocks,
            wrongState = verifier.mismatchedBlocks + verifier.mismatchedStates,
            extra = verifier.extraBlocks,
            wrongBlockEntityData = 0,
        )
    }

    override fun executeEasyPlace(
        request: LitematicaEasyPlaceRequest,
        token: LitematicaEasyPlaceExecutionToken,
    ): LitematicaEasyPlaceResult {
        validateRequest(request)?.let { return LitematicaEasyPlaceResult.Rejected(it) }
        if (EasyPlaceUtils.isHandling()) {
            return LitematicaEasyPlaceResult.Rejected("Litematica Easy Place is already handling")
        }

        val adjacent = Configs.Generic.EASY_PLACE_CLICK_ADJACENT.booleanValue
        val swing = Configs.Generic.EASY_PLACE_SWING_HAND.booleanValue
        return try {
            Configs.Generic.EASY_PLACE_CLICK_ADJACENT.booleanValue =
                request.strategy == LitematicaEasyPlaceStrategy.NEIGHBOR
            Configs.Generic.EASY_PLACE_SWING_HAND.booleanValue = false
            val handled = token.runControlled(EasyPlaceUtils::handleEasyPlaceWithMessage)
            if (handled) {
                LitematicaEasyPlaceResult.Submitted
            } else {
                LitematicaEasyPlaceResult.Failed("Litematica Easy Place did not handle the target")
            }
        } catch (failure: RuntimeException) {
            LitematicaEasyPlaceResult.Failed(failure.message ?: failure.javaClass.simpleName)
        } catch (failure: LinkageError) {
            LitematicaEasyPlaceResult.Failed(failure.message ?: failure.javaClass.simpleName)
        } finally {
            Configs.Generic.EASY_PLACE_CLICK_ADJACENT.booleanValue = adjacent
            Configs.Generic.EASY_PLACE_SWING_HAND.booleanValue = swing
        }
    }

    override fun installPositionProvider(
        provider: LitematicaPlacementPositionProvider,
    ): LitematicaPositionProviderLease {
        providerLease?.close()
        val external = IBlockPlacementPositionProvider(provider::position)
        Registry.BLOCK_PLACEMENT_POSITION_HANDLER.registerPositionProvider(external)
        return ProviderLease(external).also { providerLease = it }
    }

    override fun close() {
        try {
            providerLease?.close()
        } finally {
            scanner.close()
        }
    }

    private fun validateRequest(request: LitematicaEasyPlaceRequest): String? {
        if (request.interaction.placementId != request.placementId) return "Interaction belongs to another placement"
        if (request.interaction.position != request.targetPosition) return "Interaction belongs to another position"
        if (scanner.placement(request.placementId) == null) return "Placement is no longer loaded"
        val target = request.targetPosition.toBlockPos()
        if (Registry.BLOCK_PLACEMENT_POSITION_HANDLER.currentPlacementPosition != target) {
            return "MaLiLib position provider did not select $target"
        }
        val schematicWorld = SchematicWorldHandler.getSchematicWorld()
            ?: return "Litematica schematic world is unavailable"
        val currentDesired = Litematica262BlockSnapshotMapper.snapshot(schematicWorld.getBlockState(target))
        if (!currentDesired.sameStateAs(request.desired)) return "Schematic target changed since the local scan"
        return null
    }

    private inner class ProviderLease(
        private val external: IBlockPlacementPositionProvider,
    ) : LitematicaPositionProviderLease {
        override var isActive = true
            private set

        override fun close() {
            if (!isActive) return
            try {
                Registry.BLOCK_PLACEMENT_POSITION_HANDLER.unregisterPositionProvider(external)
            } finally {
                isActive = false
                if (providerLease === this) providerLease = null
            }
        }
    }
}

private fun net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPosition.toBlockPos() = BlockPos(x, y, z)
