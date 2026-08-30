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
import net.ccbluex.liquidbounce.features.litematica.domain.LitematicaPrintAction
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaCellInteractionSnapshot
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceExecutionToken
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceRequest
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceResult
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaEasyPlaceStrategy
import net.ccbluex.liquidbounce.features.litematica.integration.api.LitematicaPort
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.MinecraftShortcuts
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.features.rotation.RotationsValueGroup
import net.ccbluex.liquidbounce.utils.block.SwingMode
import net.ccbluex.liquidbounce.features.block.runtime.doBreak
import net.ccbluex.liquidbounce.features.block.runtime.doPlacement
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.SilentHotbarSelectionPolicy
import net.ccbluex.liquidbounce.utils.inventory.HotbarItemSlot
import net.ccbluex.liquidbounce.utils.kotlin.Priority
import net.ccbluex.liquidbounce.utils.raytracing.traceFromPlayer

enum class LitematicaExecutionResult {
    SUBMITTED,
    FAILED,
    UNAVAILABLE,
}

class MinecraftLitematicaActionDriver(
    private val module: ClientModule,
    private val rotations: RotationsValueGroup,
) : MinecraftShortcuts {

    private val preparer = LitematicaActionPreparer()
    private val slotResolver = LitematicaMaterialSlotResolver()
    private var prepared: PreparedAction? = null

    fun requestAim(
        action: LitematicaPrintAction,
        interaction: LitematicaCellInteractionSnapshot?,
        range: Double,
    ): Boolean {
        val prepared = preparer.prepare(action, interaction, range) ?: return false
        this.prepared = prepared
        RotationManager.setRotationTarget(
            rotation = prepared.rotation,
            considerInventory = true,
            valueGroup = rotations,
            priority = Priority.IMPORTANT_FOR_USAGE_1,
            provider = module,
        )
        return true
    }

    fun rotationReady(action: LitematicaPrintAction, range: Double): Boolean {
        val prepared = prepared?.takeIf { it.action == action } ?: return false
        if (RotationManager.serverRotation.directionAngleTo(prepared.rotation) > ROTATION_TOLERANCE) return false
        val hit = prepared.hitResult ?: return true
        if (prepared.action.kind == LitematicaActionKind.FLUID_PICKUP) return true
        if (prepared.strategy == LitematicaEasyPlaceStrategy.DIRECT_AIR_PLACE) return true
        val traced = traceFromPlayer(RotationManager.serverRotation, range)
        return traced.blockPos == hit.blockPos && traced.direction == hit.direction
    }

    fun execute(
        action: LitematicaPrintAction,
        materialId: String?,
        port: LitematicaPort,
        token: LitematicaEasyPlaceExecutionToken?,
        swingMode: SwingMode,
        resetDelayTicks: Int,
    ): LitematicaExecutionResult {
        val prepared = prepared?.takeIf { it.action == action } ?: return LitematicaExecutionResult.UNAVAILABLE
        val slot = slotResolver.findSlot(action, materialId) ?: return LitematicaExecutionResult.UNAVAILABLE
        if (!select(slot, resetDelayTicks)) return LitematicaExecutionResult.UNAVAILABLE

        return when (action.kind) {
            LitematicaActionKind.BREAK -> {
                doBreak(requireNotNull(prepared.hitResult), swingMode = swingMode)
                LitematicaExecutionResult.SUBMITTED
            }
            LitematicaActionKind.FLUID_PLACE,
            LitematicaActionKind.FLUID_PICKUP,
            -> {
                doPlacement(
                    requireNotNull(prepared.hitResult),
                    prepared.rotation,
                    hand = slot.useHand,
                    swingMode = swingMode,
                )
                LitematicaExecutionResult.SUBMITTED
            }
            LitematicaActionKind.PLACE,
            LitematicaActionKind.AIR_PLACE,
            -> executeEasyPlace(prepared, slot, port, token, swingMode)
        }
    }

    fun continueMining(action: LitematicaPrintAction, swingMode: SwingMode): Boolean {
        val prepared = prepared?.takeIf { it.action == action } ?: return false
        doBreak(requireNotNull(prepared.hitResult), swingMode = swingMode)
        return true
    }

    fun stopMining() {
        mc.gameMode?.stopDestroyBlock()
    }

    fun reset() {
        stopMining()
        SilentHotbar.resetSlot(module)
        prepared = null
    }

    private fun executeEasyPlace(
        prepared: PreparedAction,
        slot: HotbarItemSlot,
        port: LitematicaPort,
        token: LitematicaEasyPlaceExecutionToken?,
        swingMode: SwingMode,
    ): LitematicaExecutionResult {
        val interaction = prepared.interaction ?: return LitematicaExecutionResult.UNAVAILABLE
        val strategy = prepared.strategy ?: return LitematicaExecutionResult.UNAVAILABLE
        token ?: return LitematicaExecutionResult.UNAVAILABLE
        val request = LitematicaEasyPlaceRequest(
            placementId = prepared.action.placementId,
            targetPosition = prepared.action.target,
            desired = prepared.action.desired,
            interaction = interaction,
            strategy = strategy,
        )
        return when (port.executeEasyPlace(request, token)) {
            LitematicaEasyPlaceResult.Submitted -> {
                // The 26.2 bridge disables Litematica's own Easy Place swing for this controlled call.
                // LiquidBounce is therefore the single swing owner and can honor the configured SwingMode.
                swingMode.swing(slot.useHand)
                LitematicaExecutionResult.SUBMITTED
            }
            is LitematicaEasyPlaceResult.Rejected,
            is LitematicaEasyPlaceResult.Failed,
            -> LitematicaExecutionResult.FAILED
        }
    }

    private fun select(slot: HotbarItemSlot, resetDelayTicks: Int): Boolean =
        slot.isOffHand || SilentHotbar.selectSlotSilently(
            module,
            slot,
            resetDelayTicks.coerceAtLeast(1),
            SilentHotbarSelectionPolicy.SERVER_ONLY,
        )

    private companion object {
        const val ROTATION_TOLERANCE = 2.0f
    }
}
