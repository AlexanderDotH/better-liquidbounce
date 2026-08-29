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

import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaBridgeResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCapabilities
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCapability
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPortFactory

class Litematica262BridgeFactory : LitematicaPortFactory {

    override fun create(): LitematicaBridgeResult {
        val probe = Litematica262CapabilityProbe.probe()
        if (probe.capabilities.missingRequired().isNotEmpty()) {
            return LitematicaBridgeResult.Unsupported(probe.capabilities, probe.detail)
        }
        return LitematicaBridgeResult.Ready(Litematica262Port(probe.capabilities))
    }
}

private object Litematica262CapabilityProbe {
    private val checks = mapOf(
        LitematicaCapability.PLACEMENT_METADATA to listOf(
            MethodCheck("fi.dy.masa.litematica.data.DataManager", "getSchematicPlacementManager", 0),
            MethodCheck(
                "fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager",
                "getAllSchematicsPlacements",
                0,
            ),
            MethodCheck("fi.dy.masa.litematica.schematic.placement.SchematicPlacement", "getSubRegionBoxes", 1),
            MethodCheck(
                "fi.dy.masa.litematica.schematic.placement.SchematicPlacement",
                "getRelativeSubRegionPlacement",
                1,
            ),
            MethodCheck("fi.dy.masa.litematica.schematic.placement.SchematicPlacement", "getHashId", 0),
        ),
        LitematicaCapability.LOCAL_SCAN to listOf(
            MethodCheck("fi.dy.masa.litematica.world.SchematicWorldHandler", "getSchematicWorld", 0),
            MethodCheck("fi.dy.masa.litematica.schematic.LitematicaSchematic", "getAreaSize", 1),
            MethodCheck("fi.dy.masa.litematica.schematic.LitematicaSchematic", "getSubRegionContainer", 1),
            MethodCheck("fi.dy.masa.litematica.schematic.LitematicaSchematic", "getBlockEntityMapForRegion", 1),
            MethodCheck("fi.dy.masa.litematica.util.PositionUtils", "getReverseTransformedBlockPos", 3),
            MethodCheck("fi.dy.masa.litematica.util.PositionUtils", "getTransformedBlockPos", 3),
            MethodCheck("fi.dy.masa.litematica.util.PositionUtils", "getRelativeEndPositionFromAreaSize", 1),
            MethodCheck("fi.dy.masa.litematica.util.PositionUtils", "getMinCorner", 2),
        ),
        LitematicaCapability.MATERIALS to listOf(
            MethodCheck("fi.dy.masa.litematica.materials.MaterialCache", "getRequiredBuildItemForState", 3),
            MethodCheck("fi.dy.masa.litematica.schematic.placement.SchematicPlacement", "getMaterialList", 0),
            MethodCheck("fi.dy.masa.litematica.materials.MaterialListBase", "getMaterialsAll", 0),
            MethodCheck("fi.dy.masa.litematica.materials.MaterialListEntry", "getStack", 0),
            MethodCheck("fi.dy.masa.litematica.materials.MaterialListEntry", "getCountTotal", 0),
        ),
        LitematicaCapability.RENDER_LAYER to listOf(
            MethodCheck("fi.dy.masa.litematica.data.DataManager", "getRenderLayerRange", 0),
            MethodCheck("fi.dy.masa.malilib.util.LayerRange", "getLayerMode", 0),
            MethodCheck("fi.dy.masa.malilib.util.LayerRange", "getAxis", 0),
            MethodCheck("fi.dy.masa.malilib.util.LayerRange", "getLayerSingle", 0),
            MethodCheck("fi.dy.masa.malilib.util.LayerRange", "getLayerRangeMin", 0),
            MethodCheck("fi.dy.masa.malilib.util.LayerRange", "getLayerRangeMax", 0),
            FieldCheck("fi.dy.masa.litematica.config.Configs\$Visuals", "ENABLE_RENDERING"),
            FieldCheck("fi.dy.masa.litematica.config.Configs\$Visuals", "ENABLE_SCHEMATIC_RENDERING"),
        ),
        LitematicaCapability.EASY_PLACE_STATE to listOf(
            FieldCheck("fi.dy.masa.litematica.config.Configs\$Generic", "EASY_PLACE_MODE"),
            FieldCheck("fi.dy.masa.litematica.config.Hotkeys", "EASY_PLACE_ACTIVATION"),
            MethodCheck("fi.dy.masa.malilib.config.IConfigBoolean", "getBooleanValue", 0),
            MethodCheck("fi.dy.masa.malilib.config.IConfigBoolean", "setBooleanValue", 1),
            MethodCheck("fi.dy.masa.malilib.config.options.ConfigHotkey", "getKeybind", 0),
            MethodCheck("fi.dy.masa.malilib.hotkeys.IKeybind", "isKeybindHeld", 0),
        ),
        LitematicaCapability.EASY_PLACE_ACTION to listOf(
            MethodCheck("fi.dy.masa.litematica.util.EasyPlaceUtils", "handleEasyPlaceWithMessage", 0),
            MethodCheck("fi.dy.masa.litematica.util.EasyPlaceUtils", "isHandling", 0),
            FieldCheck("fi.dy.masa.litematica.config.Configs\$Generic", "EASY_PLACE_CLICK_ADJACENT"),
            FieldCheck("fi.dy.masa.litematica.config.Configs\$Generic", "EASY_PLACE_SWING_HAND"),
        ),
        LitematicaCapability.EASY_PLACE_SUPPRESSION to emptyList(),
        LitematicaCapability.POSITION_PROVIDER to listOf(
            FieldCheck("fi.dy.masa.malilib.registry.Registry", "BLOCK_PLACEMENT_POSITION_HANDLER"),
            MethodCheck(
                "fi.dy.masa.malilib.interoperation.BlockPlacementPositionHandler",
                "registerPositionProvider",
                1,
            ),
            MethodCheck(
                "fi.dy.masa.malilib.interoperation.BlockPlacementPositionHandler",
                "unregisterPositionProvider",
                1,
            ),
            MethodCheck(
                "fi.dy.masa.malilib.interoperation.BlockPlacementPositionHandler",
                "getCurrentPlacementPosition",
                0,
            ),
        ),
        LitematicaCapability.VERIFIER to listOf(
            MethodCheck("fi.dy.masa.litematica.schematic.placement.SchematicPlacement", "hasVerifier", 0),
            MethodCheck("fi.dy.masa.litematica.schematic.placement.SchematicPlacement", "getSchematicVerifier", 0),
            MethodCheck("fi.dy.masa.litematica.schematic.verifier.SchematicVerifier", "getCorrectStatesCount", 0),
            MethodCheck("fi.dy.masa.litematica.schematic.verifier.SchematicVerifier", "getMissingBlocks", 0),
            MethodCheck("fi.dy.masa.litematica.schematic.verifier.SchematicVerifier", "getExtraBlocks", 0),
            MethodCheck("fi.dy.masa.litematica.schematic.verifier.SchematicVerifier", "getMismatchedBlocks", 0),
            MethodCheck("fi.dy.masa.litematica.schematic.verifier.SchematicVerifier", "getMismatchedStates", 0),
        ),
    )

    fun probe(): ProbeResult {
        val failures = mutableListOf<String>()
        val supported = buildSet {
            checks.forEach { (capability, requirements) ->
                val missing = requirements.filterNot(SignatureCheck::exists)
                if (missing.isEmpty()) add(capability) else failures += missing.map(SignatureCheck::description)
            }
        }
        return ProbeResult(
            capabilities = LitematicaCapabilities.of(supported),
            detail = failures.joinToString(
                prefix = "Litematica 0.28.4 / MaLiLib 0.29.3 API mismatch: ",
            ),
        )
    }

    private sealed interface SignatureCheck {
        val description: String
        fun exists(): Boolean
    }

    private data class MethodCheck(
        val className: String,
        val methodName: String,
        val parameterCount: Int,
    ) : SignatureCheck {
        override val description: String = "$className#$methodName/$parameterCount"
        override fun exists(): Boolean = runCatching {
            externalClass(className).methods.any { it.name == methodName && it.parameterCount == parameterCount }
        }.getOrDefault(false)
    }

    private data class FieldCheck(val className: String, val fieldName: String) : SignatureCheck {
        override val description: String = "$className#$fieldName"
        override fun exists(): Boolean = runCatching {
            externalClass(className).getField(fieldName)
        }.isSuccess
    }

    data class ProbeResult(val capabilities: LitematicaCapabilities, val detail: String)

    private fun externalClass(className: String): Class<*> = Class.forName(
        className,
        false,
        Litematica262BridgeFactory::class.java.classLoader,
    )
}
